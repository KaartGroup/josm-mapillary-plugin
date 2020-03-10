// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapillary.data.mapillary;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.openstreetmap.josm.data.osm.BBox;
import org.openstreetmap.josm.data.osm.IRelation;
import org.openstreetmap.josm.data.osm.OsmPrimitiveType;
import org.openstreetmap.josm.data.osm.UniqueIdGenerator;
import org.openstreetmap.josm.data.osm.visitor.PrimitiveVisitor;

/**
 *
 */
public class MapillaryRelation extends MapillaryPrimitive implements IRelation<MapillaryRelationMember> {
  private static final UniqueIdGenerator ID_GENERATOR = new UniqueIdGenerator();
  List<MapillaryRelationMember> members = new ArrayList<>();
  @Override
  public void accept(PrimitiveVisitor visitor) {
    visitor.visit(this);
  }

  @Override
  public void visitReferrers(PrimitiveVisitor visitor) {
    this.getReferrers().forEach(i -> i.accept(visitor));
  }

  @Override
  public BBox getBBox() {
    BBox bbox = new BBox();
    this.getReferrers().forEach(i -> bbox.add(i.getBBox()));
    return bbox;
  }

  @Override
  public OsmPrimitiveType getType() {
    return OsmPrimitiveType.RELATION;
  }

  @Override
  public int getMembersCount() {
    return members.size();
  }

  @Override
  public MapillaryRelationMember getMember(int index) {
    return members.get(index);
  }

  @Override
  public List<MapillaryRelationMember> getMembers() {
    return Collections.unmodifiableList(members);
  }

  @Override
  public void setMembers(List<MapillaryRelationMember> members) {
    this.members.clear();
    this.members.addAll(members);
  }

  @Override
  public long getMemberId(int idx) {
    return this.getMember(idx).getUniqueId();
  }

  @Override
  public String getRole(int idx) {
    return this.getMember(idx).getRole();
  }

  @Override
  public OsmPrimitiveType getMemberType(int idx) {
    return this.getMember(idx).getType();
  }

  @Override
  public UniqueIdGenerator getIdGenerator() {
    return ID_GENERATOR;
  }

  @Override
  public List<MapillaryPrimitive> getReferrers(boolean allowWithoutDataset) {
    return Collections.emptyList();
  }
}
