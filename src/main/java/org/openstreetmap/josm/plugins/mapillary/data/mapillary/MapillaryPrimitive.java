// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapillary.data.mapillary;

import java.util.Map;

import org.openstreetmap.josm.data.osm.AbstractPrimitive;
import org.openstreetmap.josm.data.osm.NameFormatter;
import org.openstreetmap.josm.gui.mappaint.StyleCache;

public abstract class MapillaryPrimitive extends AbstractPrimitive {
  MapillaryData dataSet;
  @Override
  public String getDisplayName(NameFormatter formatter) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public void setHighlighted(boolean highlighted) {
    // TODO Auto-generated method stub

  }

  @Override
  public boolean isHighlighted() {
    // TODO Auto-generated method stub
    return false;
  }

  @Override
  public boolean isTagged() {
    return false;
  }

  @Override
  public boolean isAnnotated() {
    return false;
  }

  @Override
  public boolean hasDirectionKeys() {
    return false;
  }

  @Override
  public boolean reversedDirection() {
    return false;
  }

  @Override
  public MapillaryData getDataSet() {
    return dataSet;
  }

  /*----------
   * MAPPAINT
   *--------*/
  private StyleCache mappaintStyle;
  private short mappaintCacheIdx;

  @Override
  public final StyleCache getCachedStyle() {
    return mappaintStyle;
  }

  @Override
  public final void setCachedStyle(StyleCache mappaintStyle) {
    this.mappaintStyle = mappaintStyle;
  }

  @Override
  public final boolean isCachedStyleUpToDate() {
    return mappaintStyle != null && mappaintCacheIdx == dataSet.getMappaintCacheIndex();
  }

  @Override
  public final void declareCachedStyleUpToDate() {
    this.mappaintCacheIdx = dataSet.getMappaintCacheIndex();
  }

  /* end of mappaint data */

  @Override
  protected void keysChangedImpl(Map<String, String> originalKeys) {
    // Do nothing (for now)
  }

}