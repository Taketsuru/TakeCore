package jp.dip.myuminecraft.takecore;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Properties;

import org.bukkit.plugin.java.JavaPlugin;

public class Database {

    class Result {
        DatabaseTask task;
        Throwable    error;
        boolean      detached;
        boolean      done;
        boolean      waiting;

        Result(DatabaseTask task, boolean detached) {
            this.task = task;
            this.detached = detached;
        }

        void run(Connection connection) throws Throwable {
            try {
                task.run(connection);
            } catch (Throwable t) {
                if (detached) {
                    throw t;
                }
                error = t;
            }

            synchronized (this) {
                done = true;
                if (waiting) {
                    waiting = false;
                    notifyAll();
                }
            }
        }

        synchronized void sync() throws Throwable {
            assert !detached;

            while (!done) {
                waiting = true;
                wait();
            }

            if (error != null) {
                throw error;
            }
        }
    }

    JavaPlugin    plugin;
    Logger        logger;
    Connection    connection;
    Deque<Result> worklist;
    Thread        executor;
    boolean       draining;
    boolean       busy;

    public Database(JavaPlugin plugin, Logger logger) {
        this.plugin = plugin;
        this.logger = logger;
        this.worklist = new ArrayDeque<Result>();
    }

    public void enable(String url, Properties properties) throws SQLException {
        connection = DriverManager.getConnection(url, properties);
        executor = new Thread(new Runnable() {
            @Override
            public void run() {
                runTasks();
            }
        });
        executor.start();
    }

    public void disable() {
        drain();

        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
            }
            connection = null;
        }
    }

    public void submitSync(DatabaseTask job) throws Throwable {
        Result result = new Result(job, false);
        submit(result);
        result.sync();
    }

    public void submitAsync(DatabaseTask job) {
        submit(new Result(job, true));
    }

    synchronized void submit(Result result) {
        if (executor == null) {
            return;
        }

        worklist.add(result);
        if (!busy) {
            notifyAll();
        }
    }

    synchronized void drain() {
        if (executor == null) {
            return;
        }

        draining = true;
        while (draining) {
            try {
                wait();
            } catch (InterruptedException ie) {
            }
        }
    }

    void runTasks() {
        for (;;) {
            try {
                Result result;

                synchronized (this) {
                    while ((result = worklist.pollFirst()) == null) {
                        busy = false;

                        if (draining) {
                            draining = false;
                            executor = null;
                            notifyAll();
                            return;
                        }

                        wait();
                    }
                    busy = true;
                }

                result.run(connection);

                connection.setAutoCommit(true);
            } catch (Throwable t) {
                logger.warning(t, "Failed to run a database task.");
            }
        }
    }

}
