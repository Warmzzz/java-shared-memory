package be.raffaelegigantelli.sharedvalue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.RandomAccessFile;
import java.io.Serializable;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.UnaryOperator;

import be.raffaelegigantelli.sharedvalue.internal.ApplicationUtils;
import be.raffaelegigantelli.sharedvalue.internal.StringUtils;
import be.raffaelegigantelli.sharedvalue.exceptions.*;

/**
 * Represents a persistent shared value stored on disk and synchronized
 * across threads and processes using file locking.
 *
 * <p>
 * The value is serialized into a binary file located inside the system
 * temporary directory (or a custom application folder).
 * </p>
 *
 * <p>
 * This class is thread-safe and process-safe:
 * </p>
 *
 * <ul>
 *     <li>Thread safety is ensured using a {@link ReentrantReadWriteLock}</li>
 *     <li>Process safety is ensured using {@link FileLock}</li>
 * </ul>
 *
 * <p>
 * The stored type must implement {@link Serializable}.
 * </p>
 *
 * @param <T> the type of value stored
 * @author Raffaele Gigantelli
 * @version 1.0
 */
public class SharedValue<T extends Serializable> implements AutoCloseable {

    private static final String DEFAULT_APP_FOLDER = ApplicationUtils.detectDefaultAppFolder();

    private final RandomAccessFile raf;
    private final FileChannel channel;
    private final ReentrantReadWriteLock lock;

    private T value;

    /**
     * Creates a shared value using the automatically detected application folder.
     *
     * <p>
     * The storage location is based on the current Java application name.
     * </p>
     *
     * @param key the unique key identifying the shared value file
     * @param defaultValue the initial value used if no stored value exists
     *
     * @throws SharedValueInitializationException
     * if the shared storage cannot be initialized
     */
    public SharedValue(String key, T defaultValue) {
        this(DEFAULT_APP_FOLDER, key, defaultValue);
    }

    /**
     * Creates a shared value using a custom application folder.
     *
     * <p>
     * The value is persisted inside the system temporary directory
     * under the specified application folder.
     * </p>
     *
     * @param appFolder the application storage folder name
     * @param key the unique key identifying the shared value file
     * @param initialValue the initial value used if no stored value exists
     *
     * @throws NullPointerException
     * if {@code appFolder} or {@code key} is null
     *
     * @throws SharedValueInitializationException
     * if the shared storage cannot be initialized
     */
    public SharedValue(String appFolder, String key, T initialValue) {
        Objects.requireNonNull(appFolder, "appFolder cannot be null");
        Objects.requireNonNull(key, "key cannot be null");

        try {
            String sanitizedAppFolder = StringUtils.sanitize(appFolder);
            String sanitizedKey = StringUtils.sanitize(key);

            Path storageDir = Paths.get(System.getProperty("java.io.tmpdir"), sanitizedAppFolder);
            Files.createDirectories(storageDir);

            Path path = storageDir.resolve(sanitizedKey + ".bin");
            this.raf = new RandomAccessFile(path.toFile(), "rw");
            this.channel = raf.getChannel();
            this.lock = new ReentrantReadWriteLock();

            if(this.raf.length() > 0) {
                this.load();
            } else {
                this.value = initialValue;
                this.save();
            }
        } catch (IOException ioe) {
            throw new SharedValueInitializationException("Failed to initialize SharedValue: " + ioe.getMessage(), ioe);
        }
    }

    /**
     * Returns the latest stored value.
     *
     * <p>
     * The value is reloaded from disk before being returned
     * to ensure synchronization across processes.
     * </p>
     *
     * @return the current shared value
     *
     * @throws SharedValueLoadException
     * if the value cannot be loaded from storage
     */
    public T get() {
        this.lock.readLock().lock();

        try {
            this.load();
            return this.value;
        } finally {
            this.lock.readLock().unlock();
        }
    }

    /**
     * Replaces the current value and immediately persists it to disk.
     *
     * @param newValue the new value to store
     *
     * @throws SharedValueSaveException
     * if the value cannot be saved
     */
    public void set(T newValue) {
        this.lock.writeLock().lock();

        try {
            this.value = newValue;
            this.save();
        } finally {
            this.lock.writeLock().unlock();
        }
    }

    /**
     * Atomically updates the current value using the provided updater function.
     *
     * <p>
     * The current value is first reloaded from disk,
     * then transformed, and finally persisted again.
     * </p>
     *
     * @param updater the update function applied to the current value
     *
     * @throws NullPointerException
     * if {@code updater} is null
     *
     * @throws SharedValueLoadException
     * if the current value cannot be loaded
     *
     * @throws SharedValueSaveException
     * if the updated value cannot be saved
     */
    public void update(UnaryOperator<T> updater) {
        this.lock.writeLock().lock();

        try {
            this.load();
            this.value = updater.apply(this.value);
            this.save();
        } finally {
            this.lock.writeLock().unlock();
        }
    }

    private void save() {
        try(FileLock ignored = this.channel.lock()) {
            this.channel.truncate(0);
            this.channel.position(0);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try(ObjectOutputStream out = new ObjectOutputStream(baos)) {
                out.writeObject(this.value);
            }

            byte[] data = baos.toByteArray();
            this.raf.seek(0);
            this.raf.write(data);

            this.channel.force(true);
        } catch (IOException ioe) {
            throw new SharedValueSaveException("Failed to save SharedValue: " + ioe.getMessage(), ioe);
        }
    }

    @SuppressWarnings("unchecked")
    private void load() {
        try (FileLock ignored = channel.lock(0L, Long.MAX_VALUE, true)) {
            if(this.raf.length() == 0) return;
            this.raf.seek(0);

            byte[] data = new byte[(int)this.raf.length()];
            this.raf.readFully(data);

            try(ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(data))){
                this.value = (T) in.readObject();
            }
        } catch (EOFException ignored) {

        } catch (IOException | ClassNotFoundException e) {
            throw new SharedValueLoadException("Failed to load SharedValue: " + e.getMessage(), e);
        }
    }

    /**
     * Closes all underlying file resources associated with this shared value.
     *
     * <p>
     * Once closed, this instance should no longer be used.
     * </p>
     *
     * @throws SharedValueCloseException
     * if the resources cannot be closed properly
     */
    @Override
    public void close() {
        try {
            this.channel.close();
            this.raf.close();
        } catch (IOException ioe) {
            throw new SharedValueCloseException("Failed to close SharedValue: " + ioe.getMessage(), ioe);
        }
    }

}
