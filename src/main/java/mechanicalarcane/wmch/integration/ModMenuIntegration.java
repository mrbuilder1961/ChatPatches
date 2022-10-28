package mechanicalarcane.wmch.integration;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

import mechanicalarcane.wmch.WMCH;
import mechanicalarcane.wmch.config.ClothConfig;

public class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> {
            return WMCH.config instanceof ClothConfig
                ? ((ClothConfig)WMCH.config).getWMCHConfigScreen(parent)
                : null
            ;
        };
    }
}