// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapillary.data.mapillary;

import org.openstreetmap.josm.data.osm.IPrimitive;
import org.openstreetmap.josm.data.osm.QuadBucketPrimitiveStore;

/**
 * This class sole purpose is to allow QuadBucketPrimitiveStore to be use in Mapillary
 */
public class QuadBucketMapillaryPrimitiveStore
    extends QuadBucketPrimitiveStore<MapillaryAbstractImage, MapillarySequence, MapillaryRelation> {

  boolean modified;
  @Override
  protected void removePrimitive(IPrimitive primitive) {
    // This is literally to allow use to call this method in the plugin.
    super.removePrimitive(primitive);
    modified = true; // Fairly useless, right now.
  }
}
