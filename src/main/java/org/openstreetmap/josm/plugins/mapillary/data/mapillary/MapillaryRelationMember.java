// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapillary.data.mapillary;

import org.openstreetmap.josm.data.osm.INode;
import org.openstreetmap.josm.data.osm.IRelation;
import org.openstreetmap.josm.data.osm.IRelationMember;
import org.openstreetmap.josm.data.osm.IWay;
import org.openstreetmap.josm.data.osm.OsmPrimitiveType;

/**
 *
 */
public class MapillaryRelationMember implements IRelationMember<MapillaryPrimitive> {
  MapillaryPrimitive primitive;
  String role;

  @Override
  public long getUniqueId() {
    // TODO Auto-generated method stub
    return 0;
  }

  @Override
  public OsmPrimitiveType getType() {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public boolean isNew() {
    // TODO Auto-generated method stub
    return false;
  }

  @Override
  public String getRole() {
    return role;
  }

  @Override
  public boolean isNode() {
    return primitive instanceof INode;
  }

  @Override
  public boolean isWay() {
    return primitive instanceof IWay<?>;
  }

  @Override
  public boolean isRelation() {
    return primitive instanceof IRelation<?>;
  }

  @Override
  public MapillaryPrimitive getMember() {
    return primitive;
  }
}
