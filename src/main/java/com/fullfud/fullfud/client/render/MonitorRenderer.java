package com.fullfud.fullfud.client.render;

import com.fullfud.fullfud.client.model.MonitorModel;
import com.fullfud.fullfud.common.item.MonitorItem;
import software.bernie.geckolib.renderer.GeoItemRenderer;

public class MonitorRenderer extends GeoItemRenderer<MonitorItem> {
    public MonitorRenderer() {
        super(new MonitorModel());
    }
}