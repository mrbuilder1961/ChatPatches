package mechanicalarcane.wmch.util;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/** Allows loading the NCR mod config without it crashing if not installed */
public final class NCRConfigAccessor {
    public static boolean chatToSys() {
        try {
            Method method = Class.forName("com.aizistral.nochatreports.handlers.NoReportsConfig").getMethod("convertsToGameMessage", new Class[0]);

            return (Boolean) method.invoke(null, new Object[0]);
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            return false;
        }
    }
}
