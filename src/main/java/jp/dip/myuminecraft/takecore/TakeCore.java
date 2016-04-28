package jp.dip.myuminecraft.takecore;

import java.util.Locale;
import java.util.ResourceBundle;

import org.bukkit.plugin.java.JavaPlugin;

public class TakeCore extends JavaPlugin {

    Logger    logger;
    Messages  messages;
    SignTable signTable;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        logger = new Logger(getLogger());
        Locale locale = Messages.getLocale(getConfig().getString("locale"));
        messages = new Messages(ResourceBundle.getBundle("messages", locale), locale);
        signTable = new SignTable(this, logger, messages);
    }

    @Override
    public void onDisable() {
        signTable.onDisable();

        logger = null;
        messages = null;
        signTable = null;
    }

    public SignTable getSignTable() {
        return signTable;
    }
}
