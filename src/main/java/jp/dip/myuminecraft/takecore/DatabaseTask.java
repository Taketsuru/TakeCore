package jp.dip.myuminecraft.takecore;

import java.sql.Connection;

public interface DatabaseTask {
    public void run(Connection connection) throws Throwable;
}
