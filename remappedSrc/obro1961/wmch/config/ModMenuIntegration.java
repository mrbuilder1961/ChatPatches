package obro1961.wmch.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

import obro1961.wmch.WMCH;

public class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> { return WMCH.config.getClothConfig().getWMCHConfigScreen(parent); };
    }
}