package com.fullfud.fullfud.client.render;

import com.fullfud.fullfud.client.model.FpvGogglesModel;
import com.fullfud.fullfud.common.item.FpvGogglesItem;
import software.bernie.geckolib.renderer.GeoArmorRenderer;

public class FpvGogglesRenderer extends GeoArmorRenderer<FpvGogglesItem> {
    public FpvGogglesRenderer() {
        super(new FpvGogglesModel());
    }
}
