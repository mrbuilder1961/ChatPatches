package obro1961.chatpatches.integration;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import dev.kikugie.fletching_table.annotation.fabric.Entrypoint;
import obro1961.chatpatches.ChatPatches;

@Entrypoint("modmenu")
public class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return ChatPatches.config::getConfigScreen;
    }
}