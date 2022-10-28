package mechanicalarcane.wmch.integration;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/** Allows loading the NCR mod config without it crashing if not installed */
public final class NCRConfigAccessor {

    /**
     * Returns {@code true} if NoChatReports is
     * installed AND the {@code convertToGameMessage}
     * feature is enabled
     */
    public static boolean chatToSys() {
        try {
            final String ncrConfigPath = "com.aizistral.nochatreports.config.NCRConfig";
            final Class<?>[] emptyParams = new Class[0];
            final Object[] emptyArgs = new Object[0];

            Object ncrCommon = Class.forName(ncrConfigPath).getMethod("getCommon", emptyParams).invoke(null, emptyArgs);
            Method chatToSys = Class.forName(ncrConfigPath + "Common").getMethod("convertToGameMessage", emptyParams);

            return (Boolean)chatToSys.invoke(ncrCommon, emptyArgs);
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            return false;
        }
    }
}
