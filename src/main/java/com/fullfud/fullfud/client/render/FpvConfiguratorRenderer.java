package com.fullfud.fullfud.client.render;

import com.fullfud.fullfud.client.model.FpvConfiguratorModel;
import com.fullfud.fullfud.common.item.FpvConfiguratorItem;
import software.bernie.geckolib.renderer.GeoItemRenderer;

public class FpvConfiguratorRenderer extends GeoItemRenderer<FpvConfiguratorItem> {
    public FpvConfiguratorRenderer() {
        super(new FpvConfiguratorModel());
    }
}
