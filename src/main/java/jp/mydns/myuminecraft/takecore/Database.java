package jp.mydns.myuminecraft.takecore;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.Properties;
import java.util.Queue;

public class Database {

    private static class Result {
        private DatabaseTask task;
        private Throwable    error;
        private boolean      waitJoin;
        private boolean      waitFinish;
        private boolean      detached;

        Result(DatabaseTask task, boolean detached) {
            this.task = task;
            this.detached = detached;
            waitJoin = waitFinish = false;
        }

        void run(Connection connection) {
            try {
                task.run(connection);

            } catch (Throwable e) {
                error = e;

            } finally {
                if (detached) {
                    return;
                }

                synchronized (this) {
                    if (waitFinish) {
                        waitFinish = false;
                        notifyAll();
                    } else {
                        waitJoin = true;
                        while (waitJoin) {
                            try {
                                wait();
                            } catch (InterruptedException e) {
                            }
                        }
                    }
                }
            }
        }

        synchronized void join() throws Throwable {
            assert !detached;

            if (waitJoin) {
                waitJoin = false;
                notifyAll();
            } else {
                waitFinish = true;
                while (waitFinish) {
                    try {
                        wait();
                    } catch (InterruptedException e) {
                    }
                }
            }

            if (error != null) {
                throw error;
            }
        }
    }

    private Logger        logger;
    private Connection    connection;
    private Queue<Result> queue = new ArrayDeque<Result>();
    private boolean       idling;

    public Database(Logger logger) {
        this.logger = logger;
    }

    public void enable(String url, Properties properties) throws SQLException {
        connection = DriverManager.getConnection(url, properties);
        new Thread(new Runnable() {
            @Override
            public void run() {
                runTasks();
            }
        }).start();
    }

    public void disable() {
        if (connection == null) {
            return;
        }

        try {
            submitSync(new DatabaseTask() {
                @Override
                public void run(Connection dummy) throws SQLException {
                    connection.close();
                    connection = null;
                }
            });
        } catch (Throwable e) {
        }
    }

    public void submitSync(DatabaseTask job) throws Throwable {
        Result result = new Result(job, false);
        submit(result);
        result.join();
    }

    public void submitAsync(DatabaseTask job) {
        submit(new Result(job, true));
    }

    private synchronized void submit(Result result) {
        if (connection == null) {
            return;
        }
        queue.add(result);
        if (idling) {
            notifyAll();
        }
    }

    private void runTasks() {
        while (connection != null) {
            try {
                connection.setAutoCommit(true);

                Result result = null;

                synchronized (this) {
                    if ((result = queue.poll()) == null) {
                        idling = true;
                        try {
                            wait();
                        } catch (InterruptedException e) {
                        }
                        idling = false;
                        continue;
                    }
                }

                result.run(connection);

            } catch (SQLException e) {
                logger.warning(e, "Failed to run a database task.");
            }
        }
    }
}
