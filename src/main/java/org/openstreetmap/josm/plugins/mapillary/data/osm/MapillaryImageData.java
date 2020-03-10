// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapillary.data.osm;

import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.BBox;
import org.openstreetmap.josm.data.osm.INode;
import org.openstreetmap.josm.data.osm.OsmPrimitiveType;
import org.openstreetmap.josm.data.osm.PrimitiveData;
import org.openstreetmap.josm.data.osm.UniqueIdGenerator;
import org.openstreetmap.josm.data.osm.visitor.PrimitiveVisitor;
import org.openstreetmap.josm.data.projection.ProjectionRegistry;
import org.openstreetmap.josm.plugins.mapillary.data.mapillary.MapillaryAbstractImage;

/**
 *
 */
public class MapillaryImageData extends PrimitiveData implements INode {

  private static final long serialVersionUID = 5626323599550908773L;
  private static final UniqueIdGenerator idGenerator = MapillaryAbstractImage.UNIQUE_ID_GENERATOR;
  /*
   * we "inline" lat/lon coordinates instead of using a LatLon => reduces memory footprint
   */
  private double lat = Double.NaN;
  private double lon = Double.NaN;

  /**
   * Constructs a new {@code NodeData}.
   */
  public MapillaryImageData() {
    // contents can be set later with setters
    this(idGenerator.currentUniqueId());
    idGenerator.advanceUniqueId(id);
  }

  /**
   * Constructs a new {@code MapillaryImageData} with given id.
   *
   * @param id id
   */
  public MapillaryImageData(long id) {
    super(id);
  }

  /**
   * Constructs a new {@code NodeData}.
   *
   * @param data node data to copy
   */
  public MapillaryImageData(MapillaryImageData data) {
    super(data);
    setCoor(data.getCoor());
  }

  @Override
  public double lat() {
    return lat;
  }

  @Override
  public double lon() {
    return lon;
  }

  @Override
  public boolean isLatLonKnown() {
    return !Double.isNaN(lat) && !Double.isNaN(lon);
  }

  @Override
  public LatLon getCoor() {
    return isLatLonKnown() ? new LatLon(lat, lon) : null;
  }

  @Override
  public final void setCoor(LatLon coor) {
    if (coor == null) {
      this.lat = Double.NaN;
      this.lon = Double.NaN;
    } else {
      this.lat = coor.lat();
      this.lon = coor.lon();
    }
  }

  @Override
  public void setEastNorth(EastNorth eastNorth) {
    setCoor(ProjectionRegistry.getProjection().eastNorth2latlon(eastNorth));
  }

  @Override
  public MapillaryImageData makeCopy() {
    return new MapillaryImageData(this);
  }

  @Override
  public String toString() {
    return super.toString() + " NODE " + getCoor();
  }

  @Override
  public OsmPrimitiveType getType() {
    return OsmPrimitiveType.NODE;
  }

  @Override
  public void accept(PrimitiveVisitor visitor) {
    visitor.visit(this);
  }

  @Override
  public BBox getBBox() {
    return new BBox(lon, lat);
  }

  @Override
  public boolean isReferredByWays(int n) {
    return false;
  }

  @Override
  public UniqueIdGenerator getIdGenerator() {
    return idGenerator;
  }
}
