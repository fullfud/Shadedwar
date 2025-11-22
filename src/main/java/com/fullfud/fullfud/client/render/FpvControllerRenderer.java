package com.fullfud.fullfud.client.render;

import com.fullfud.fullfud.client.model.FpvControllerModel;
import com.fullfud.fullfud.common.item.FpvControllerItem;
import software.bernie.geckolib.renderer.GeoItemRenderer;

public class FpvControllerRenderer extends GeoItemRenderer<FpvControllerItem> {
    public FpvControllerRenderer() {
        super(new FpvControllerModel());
    }
}