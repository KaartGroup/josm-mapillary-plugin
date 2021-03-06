// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapillary.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.geom.Path2D;

import org.junit.jupiter.api.Test;

class ImageDetectionTest {
  @Test
  void test() {
    ImageDetection<Path2D.Double> id = new ImageDetection<>(new Path2D.Double(), "imgKey", "key", 0.123, "packageName",
      "value");
    ImageDetection<Path2D.Double> trafficsign = new ImageDetection<>(new Path2D.Double(), "imgKey2", "key2", 4.567,
      "trafficsign", "value2");

    assertEquals("imgKey", id.getImageKey());
    assertEquals("imgKey2", trafficsign.getImageKey());

    assertEquals("key", id.getKey());
    assertEquals("key2", trafficsign.getKey());

    assertEquals("packageName", id.getPackage());
    assertEquals("trafficsign", trafficsign.getPackage());

    assertEquals(0.123, id.getScore(), 1e-9);
    assertEquals(4.567, trafficsign.getScore(), 1e-9);

    assertFalse(id.isTrafficSign());
    assertTrue(trafficsign.isTrafficSign());

    assertEquals("value", id.getUnknownValue());
    assertEquals("value2", trafficsign.getUnknownValue());
  }
}
