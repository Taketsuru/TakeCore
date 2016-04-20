package jp.dip.myuminecraft.takecore;

import java.io.UnsupportedEncodingException;
import java.util.IllformedLocaleException;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

import org.bukkit.command.CommandSender;

public class Messages {

    ResourceBundle bundle;
    Locale         locale;

    public Messages(Locale locale) {
        bundle = ResourceBundle.getBundle("messages", locale);
        this.locale = locale;
    }

    public Messages(String languageTag) throws IllformedLocaleException {
        Locale locale = languageTag != null
                ? new Locale.Builder().setLanguageTag(languageTag).build()
                : new Locale("en-US");
        bundle = ResourceBundle.getBundle("messages", locale);
        this.locale = locale;
    }

    public String getString(String key) {
        try {
            return new String(bundle.getString(key).getBytes("ISO-8859-1"),
                    "UTF-8");
        } catch (MissingResourceException mre) {
            return key;
        } catch (UnsupportedEncodingException e) {
            throw new Error(e);
        }
    }

    public void send(CommandSender sender, String formatKey, Object... args) {
        sender.sendMessage(String.format(locale, getString(formatKey), args));
    }

}
