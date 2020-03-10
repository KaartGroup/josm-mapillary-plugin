// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapillary.data.mapillary;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.json.Json;
import javax.json.JsonException;
import javax.json.JsonReader;
import javax.swing.SwingUtilities;

import org.apache.commons.jcs.access.CacheAccess;

import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.DataSource;
import org.openstreetmap.josm.data.cache.BufferedImageCacheEntry;
import org.openstreetmap.josm.data.osm.BBox;
import org.openstreetmap.josm.data.osm.DataSelectionListener;
import org.openstreetmap.josm.data.osm.DownloadPolicy;
import org.openstreetmap.josm.data.osm.HighlightUpdateListener;
import org.openstreetmap.josm.data.osm.OsmData;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.PrimitiveId;
import org.openstreetmap.josm.data.osm.Storage;
import org.openstreetmap.josm.data.osm.UploadPolicy;
import org.openstreetmap.josm.data.osm.WaySegment;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MapView;
import org.openstreetmap.josm.plugins.mapillary.MapillaryPlugin;
import org.openstreetmap.josm.plugins.mapillary.cache.CacheUtils;
import org.openstreetmap.josm.plugins.mapillary.cache.Caches;
import org.openstreetmap.josm.plugins.mapillary.gui.MapillaryMainDialog;
import org.openstreetmap.josm.plugins.mapillary.gui.imageinfo.ImageInfoPanel;
import org.openstreetmap.josm.plugins.mapillary.gui.layer.MapillaryLayer;
import org.openstreetmap.josm.plugins.mapillary.gui.layer.PointObjectLayer;
import org.openstreetmap.josm.plugins.mapillary.model.ImageDetection;
import org.openstreetmap.josm.plugins.mapillary.oauth.MapillaryUser;
import org.openstreetmap.josm.plugins.mapillary.oauth.OAuthUtils;
import org.openstreetmap.josm.plugins.mapillary.utils.MapillaryProperties;
import org.openstreetmap.josm.plugins.mapillary.utils.MapillaryURL;
import org.openstreetmap.josm.plugins.mapillary.utils.api.JsonDecoder;
import org.openstreetmap.josm.plugins.mapillary.utils.api.JsonImageDetectionDecoder;
import org.openstreetmap.josm.tools.HttpClient;
import org.openstreetmap.josm.tools.Logging;
import org.openstreetmap.josm.tools.SubclassFilteredCollection;
import org.openstreetmap.josm.tools.Utils;

/**
 * Database class for all the {@link MapillaryAbstractImage} objects.
 *
 * @author nokutu, major modifications by Taylor Smock
 * @see MapillaryAbstractImage
 * @see MapillarySequence
 */
public class MapillaryData
    implements OsmData<MapillaryPrimitive, MapillaryAbstractImage, MapillarySequence, MapillaryRelation> {
  private final QuadBucketMapillaryPrimitiveStore store = new QuadBucketMapillaryPrimitiveStore();
  private final Set<MapillaryAbstractImage> images = ConcurrentHashMap.newKeySet();
  private final Storage<MapillaryPrimitive> allPrimitives = new Storage<>(new Storage.PrimitiveIdHash(), true);
  /**
   * The image currently selected, this is the one being shown.
   */
  private MapillaryAbstractImage selectedImage;
  /**
   * The image under the cursor.
   */
  private MapillaryAbstractImage highlightedImage;
  /**
   * All the images selected, can be more than one.
   */
  private final Collection<MapillaryPrimitive> selectedPrimitives = ConcurrentHashMap.newKeySet();
  /**
   * Listeners of the class.
   */
  private final List<MapillaryDataListener> listeners = new CopyOnWriteArrayList<>();
  /**
   * The bounds of the areas for which the pictures have been downloaded.
   */
  private final List<DataSource> dataSources;

  /**
   * Keeps track of images where detections have been fully downloaded
   */
  private final Set<MapillaryAbstractImage> fullyDownloadedDetections = ConcurrentHashMap.newKeySet();

  /** The dataset name */
  private String name;

  /** The dataset lock */
  ReadWriteLock readWriteLock = new ReentrantReadWriteLock();

  /** The download policy */
  DownloadPolicy downloadPolicy = DownloadPolicy.NORMAL;
  /** The upload policy */
  UploadPolicy uploadPolicy = UploadPolicy.NORMAL;
  private short mappaintCacheIdx = 1;

  /**
   * Creates a new object and adds the initial set of listeners.
   */
  public MapillaryData() {
    this.selectedImage = null;
    this.dataSources = new ArrayList<>();

    // Adds the basic set of listeners.
    Arrays.stream(MapillaryPlugin.getMapillaryDataListeners()).forEach(this::addListener);
    if (MainApplication.getMainFrame() != null) {
      addListener(MapillaryMainDialog.getInstance());
      addListener(ImageInfoPanel.getInstance());
    }
  }

  /**
   * Adds an MapillaryImage to the object, and then repaints mapView.
   *
   * @param image The image to be added.
   */
  public void add(MapillaryAbstractImage image) {
    add(image, true);
  }

  /**
   * Adds a MapillaryImage to the object, but doesn't repaint mapView. This is
   * needed for concurrency.
   *
   * @param image  The image to be added.
   * @param update Whether the map must be updated or not.
   * @throws NullPointerException if parameter <code>image</code> is <code>null</code>
   */
  public void add(MapillaryAbstractImage image, boolean update) {
    store.addPrimitive(image);
    images.add(image);
    if (update) {
      MapillaryLayer.invalidateInstance();
    }
    fireImagesAdded();
  }

  /**
   * Adds a set of MapillaryImages to the object, and then repaints mapView.
   *
   * @param images The set of images to be added.
   */
  public void addAll(Collection<? extends MapillaryAbstractImage> images) {
    addAll(images, true);
  }

  /**
   * Adds a set of {link MapillaryAbstractImage} objects to this object.
   *
   * @param newImages The set of images to be added.
   * @param update Whether the map must be updated or not.
   */
  public void addAll(Collection<? extends MapillaryAbstractImage> newImages, boolean update) {
    newImages.forEach(store::addPrimitive);
    images.addAll(newImages);
    if (update) {
      MapillaryLayer.invalidateInstance();
    }
    fireImagesAdded();
  }

  /**
   * Adds a new listener.
   *
   * @param lis Listener to be added.
   */
  public final void addListener(final MapillaryDataListener lis) {
    listeners.add(lis);
  }

  /**
   * Adds a {@link MapillaryImage} object to the list of selected images, (when
   * ctrl + click)
   *
   * @param image The {@link MapillaryImage} object to be added.
   */
  public void addMultiSelectedImage(final MapillaryAbstractImage image) {
    if (!this.selectedPrimitives.contains(image)) {
      if (this.getSelectedImage() == null) {
        this.setSelectedImage(image);
      } else {
        this.selectedPrimitives.add(image);
      }
    }
    MapillaryLayer.invalidateInstance();
  }

  /**
   * Adds a set of {@code MapillaryAbstractImage} objects to the list of
   * selected images.
   *
   * @param images A {@link Collection} object containing the set of images to be added.
   */
  public void addMultiSelectedImage(Collection<MapillaryAbstractImage> images) {
    images.stream().filter(image -> !this.selectedPrimitives.contains(image)).forEach(image -> {
      if (this.getSelectedImage() == null) {
        this.setSelectedImage(image);
      } else {
        this.selectedPrimitives.add(image);
      }
    });
    MapillaryLayer.invalidateInstance();
  }

  public List<Bounds> getBounds() {
    return dataSources.stream().map(b -> b.bounds).collect(Collectors.toCollection(CopyOnWriteArrayList::new));
  }

  /**
   * Removes an image from the database. From the {@link List} in this object
   * and from its {@link MapillarySequence}.
   *
   * @param image The {@link MapillaryAbstractImage} that is going to be deleted.
   */
  public void remove(MapillaryAbstractImage image) {
    store.removePrimitive(image);
    images.remove(image);
    if (getMultiSelectedImages().contains(image)) {
      setSelectedImage(null);
    }
    if (image.getSequence() != null) {
      image.getSequence().remove(image);
    }
    MapillaryLayer.invalidateInstance();
  }

  /**
   * Removes a set of images from the database.
   *
   * @param images A {@link Collection} of {@link MapillaryAbstractImage} objects that are
   *               going to be removed.
   */
  public void remove(Collection<MapillaryAbstractImage> images) {
    images.forEach(this::remove);
  }

  /**
   * Removes a listener.
   *
   * @param lis Listener to be removed.
   */
  public void removeListener(MapillaryDataListener lis) {
    this.listeners.remove(lis);
  }

  /**
   * Highlights the image under the cursor.
   *
   * @param image The image under the cursor.
   */
  public void setHighlightedImage(MapillaryAbstractImage image) {
    this.highlightedImage = image;
  }

  /**
   * Returns the image under the mouse cursor.
   *
   * @return The image under the mouse cursor.
   */
  public MapillaryAbstractImage getHighlightedImage() {
    return this.highlightedImage;
  }

  /**
   * Returns a Set containing all images.
   *
   * @return A Set object containing all images.
   */
  public synchronized Set<MapillaryAbstractImage> getImages() {
    return images;
  }

  /**
   * Get a specific MapillaryImage
   *
   * @param key The key for the MapillaryImage
   * @return The MapillaryImage or {@code null}
   */
  public MapillaryImage getImage(String key) {
    return getImages().parallelStream().filter(MapillaryImage.class::isInstance)
        .map(MapillaryImage.class::cast).filter(m -> m.getKey().equals(key)).findAny().orElse(null);
  }

  /**
   * Returns a Set of all sequences, that the images are part of.
   * @return all sequences that are contained in the Mapillary data
   */
  public Set<MapillarySequence> getSequences() {
    return images.stream().map(MapillaryAbstractImage::getSequence).collect(Collectors.toSet());
  }

  /**
   * Returns the MapillaryImage object that is currently selected.
   *
   * @return The selected MapillaryImage object.
   */
  public MapillaryAbstractImage getSelectedImage() {
    return this.selectedImage;
  }

  private void fireImagesAdded() {
    listeners.stream().filter(Objects::nonNull).forEach(MapillaryDataListener::imagesAdded);
  }

  /**
   * Selects a new image.If the user does ctrl + click, this isn't triggered.
   *
   * @param image The MapillaryImage which is going to be selected
   */
  public void setSelectedImage(MapillaryAbstractImage image) {
    setSelectedImage(image, false);
  }

  /**
   * Selects a new image.If the user does ctrl+click, this isn't triggered. You
   * can choose whether to center the view on the new image or not.
   *
   * @param image The {@link MapillaryImage} which is going to be selected.
   * @param zoom  True if the view must be centered on the image; false otherwise.
   */
  public void setSelectedImage(MapillaryAbstractImage image, boolean zoom) {
    MapillaryAbstractImage oldImage = this.selectedImage;
    if (image instanceof MapillaryImage && inCurrentlySelectedDetection((MapillaryImage) image)) {
      getAllDetections(Collections.singleton((MapillaryImage) image));
    }
    this.selectedImage = image;
    this.selectedPrimitives.clear();
    final MapView mv = MapillaryPlugin.getMapView();
    if (image != null) {
      this.selectedPrimitives.add(image);
      if (mv != null && image instanceof MapillaryImage) {
        MapillaryImage mapillaryImage = (MapillaryImage) image;

        // Downloading thumbnails of surrounding pictures.
        downloadSurroundingImages(mapillaryImage);
      }
    }
    if (mv != null && zoom && selectedImage != null) {
      mv.zoomTo(selectedImage.getMovingLatLon());
    }
    fireSelectedImageChanged(oldImage, this.selectedImage);
    MapillaryLayer.invalidateInstance();
  }

  /**
   * Check if the image has a selected point object
   *
   * @param image The image to check
   * @return {@code true} if any point object layer has a selected object with the image key.
   */
  public static boolean inCurrentlySelectedDetection(MapillaryImage image) {
    return MainApplication.getLayerManager().getLayersOfType(PointObjectLayer.class).parallelStream()
        .map(PointObjectLayer::getDataSet).flatMap(d -> d.getSelected().parallelStream())
        .filter(p -> p.hasTag("detections"))
        .flatMap(p -> PointObjectLayer.parseDetections(p.get("detections")).parallelStream())
        .anyMatch(p -> image.getKey().equals(p.getOrDefault("image_key", null)));
  }

  /**
   * Downloads surrounding images of this mapillary image in background threads
   * @param mapillaryImage the image for which the surrounding images should be downloaded
   */
  private static void downloadSurroundingImages(MapillaryImage mapillaryImage) {
    MainApplication.worker.execute(() -> {
      final int prefetchCount = MapillaryProperties.PRE_FETCH_IMAGE_COUNT.get();
      CacheAccess<String, BufferedImageCacheEntry> imageCache = Caches.ImageCache.getInstance().getCache();

      MapillaryAbstractImage nextImage = mapillaryImage.next();
      MapillaryAbstractImage prevImage = mapillaryImage.previous();

      for (int i = 0; i < prefetchCount; i++) {
        if (nextImage != null) {
          if ((nextImage instanceof MapillaryImage) &&
            (imageCache.get(((MapillaryImage) nextImage).getKey()) == null)) {
            CacheUtils.downloadPicture((MapillaryImage) nextImage);
          }
          nextImage = nextImage.next();
        }
        if (prevImage != null) {
          if ((prevImage instanceof MapillaryImage) &&
            (imageCache.get(((MapillaryImage) prevImage).getKey()) == null)) {
            CacheUtils.downloadPicture((MapillaryImage) prevImage);
          }
          prevImage = prevImage.previous();
        }
      }
    });
  }

  private void fireSelectedImageChanged(MapillaryAbstractImage oldImage, MapillaryAbstractImage newImage) {
    listeners.stream().filter(Objects::nonNull).forEach(lis -> lis.selectedImageChanged(oldImage, newImage));
  }

  /**
   * Returns a List containing all {@code MapillaryAbstractImage} objects
   * selected with ctrl + click.
   *
   * @return A List object containing all the images selected.
   */
  public Set<MapillaryAbstractImage> getMultiSelectedImages() {
    return new TreeSet<>(Utils.filteredCollection(selectedPrimitives, MapillaryAbstractImage.class));
  }

  /**
   * Sets a new {@link Collection} object as the used set of images.
   * Any images that are already present, are removed.
   *
   * @param newImages the new image list (previously set images are completely replaced)
   */
  public void setImages(Collection<MapillaryAbstractImage> newImages) {
    synchronized (this) {
      images.clear();
      images.addAll(newImages);
      store.clear();
      newImages.forEach(store::addPrimitive);
    }
  }

  @Override
  public Collection<DataSource> getDataSources() {
    return Collections.unmodifiableCollection(dataSources);
  }

  /**
   * Get all the detections for an image
   *
   * @param imagesToGet The images to get detections for
   */
  public void getAllDetections(Collection<MapillaryImage> imagesToGet) {
    List<MapillaryImage> list = imagesToGet.stream().filter(Objects::nonNull)
        .collect(Collectors.toCollection(ArrayList::new));
    int index = list.indexOf(getSelectedImage());
    MapillaryImage current = index >= 0 ? list.get(index) : null;
    if (current != null) {
      list.remove(current);
    }
    if (SwingUtilities.isEventDispatchThread()) {
      MainApplication.worker.execute(() -> {
        getDetections(Collections.singleton(current));
        getDetections(list);
      });
    } else {
      getDetections(Collections.singleton(current));
      getDetections(list);
    }
  }

  private void getDetections(Collection<MapillaryImage> imagesToGetDetections) {
    if (imagesToGetDetections == null || imagesToGetDetections.isEmpty()) {
      return;
    }
    synchronized (fullyDownloadedDetections) {
      Collection<MapillaryImage> imagesToGet = imagesToGetDetections.stream()
          .filter(Objects::nonNull)
          .filter(i -> !fullyDownloadedDetections.contains(i)).collect(Collectors.toList());
      URL nextUrl = MapillaryURL.APIv3
          .retrieveDetections(imagesToGet.stream().map(MapillaryImage::getKey).collect(Collectors.toList()));
      Map<String, List<ImageDetection>> detections = new HashMap<>();
      while (nextUrl != null) {
        HttpClient client = HttpClient.create(nextUrl);
        if (MapillaryUser.getUsername() != null)
          OAuthUtils.addAuthenticationHeader(client);
        try (JsonReader reader = createJsonReader(client)) {
          Map<String, List<ImageDetection>> tMap = JsonDecoder
              .decodeFeatureCollection(reader.readObject(), JsonImageDetectionDecoder::decodeImageDetection).stream()
              .collect(Collectors.groupingBy(ImageDetection::getImageKey));
          tMap.forEach((key, detection) -> {
            List<ImageDetection> detectionList = detections.getOrDefault(key, new ArrayList<>());
            detections.putIfAbsent(key, detectionList);
            detectionList.addAll(detection);
          });
          nextUrl = MapillaryURL.APIv3.parseNextFromLinkHeaderValue(client.getResponse().getHeaderField("Link"));
        } catch (IOException | JsonException | NumberFormatException e) {
          Logging.error(e);
          nextUrl = null; // Ensure we don't keep looping if there is an error.
        }
      }
      imagesToGet.forEach(i -> {
        i.setAllDetections(detections.get(i.getKey()));
        fullyDownloadedDetections.add(i);
      });
      if (imagesToGet.contains(getSelectedImage())) {
        MapillaryMainDialog.getInstance().mapillaryImageDisplay
          .setAllDetections(((MapillaryImage) getSelectedImage()).getDetections());
      }
    }
  }

  private static JsonReader createJsonReader(HttpClient client) throws IOException {
    client.connect();
    return Json.createReader(client.getResponse().getContent());
  }

  @Override
  public void lock() {
    readWriteLock.readLock().lock();
  }

  @Override
  public void unlock() {
    readWriteLock.readLock().unlock();
  }

  @Override
  public boolean isLocked() {
    boolean locked = false;
    try {
      locked = readWriteLock.readLock().tryLock();
      return locked;
    } finally {
      if (locked) {
        readWriteLock.readLock().unlock();
      }
    }
  }

  @Override
  public String getVersion() {
    return null;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public void setName(String name) {
    this.name = name;
  }

  @Override
  public void addPrimitive(MapillaryPrimitive primitive) {
    if (primitive instanceof MapillaryAbstractImage) {
      images.add((MapillaryAbstractImage) primitive);
      store.addPrimitive(primitive);
      fireImagesAdded();
    }
  }

  @Override
  public void clear() {
    store.clear();
    images.clear();
  }

  @Override
  public List<MapillaryAbstractImage> searchNodes(BBox bbox) {
    return store.searchNodes(bbox);
  }

  @Override
  public boolean containsNode(MapillaryAbstractImage n) {
    return store.containsNode(n);
  }

  @Override
  public List<MapillarySequence> searchWays(BBox bbox) {
    return store.searchWays(bbox);
  }

  @Override
  public boolean containsWay(MapillarySequence w) {
    return store.containsWay(w);
  }

  @Override
  public List<MapillaryRelation> searchRelations(BBox bbox) {
    return store.searchRelations(bbox);
  }

  @Override
  public boolean containsRelation(MapillaryRelation r) {
    return store.containsRelation(r);
  }

  @Override
  public MapillaryPrimitive getPrimitiveById(PrimitiveId primitiveId) {
    return null; // TODO implement?
  }

  @Override
  public <T extends MapillaryPrimitive> Collection<T> getPrimitives(Predicate<? super MapillaryPrimitive> predicate) {
    return new SubclassFilteredCollection<>(allPrimitives, predicate);
  }

  @Override
  public Collection<MapillaryAbstractImage> getNodes() {
    return getPrimitives(MapillaryAbstractImage.class::isInstance);
  }

  @Override
  public Collection<MapillarySequence> getWays() {
    return getPrimitives(MapillarySequence.class::isInstance);
  }

  @Override
  public Collection<MapillaryRelation> getRelations() {
    return getPrimitives(MapillaryRelation.class::isInstance);
  }

  @Override
  public DownloadPolicy getDownloadPolicy() {
    return downloadPolicy;
  }

  @Override
  public void setDownloadPolicy(DownloadPolicy downloadPolicy) {
    this.downloadPolicy = downloadPolicy;
  }

  @Override
  public UploadPolicy getUploadPolicy() {
    return uploadPolicy;
  }

  @Override
  public void setUploadPolicy(UploadPolicy uploadPolicy) {
    this.uploadPolicy = uploadPolicy;
  }

  @Override
  public Lock getReadLock() {
    return readWriteLock.readLock();
  }

  @Override
  public Collection<WaySegment> getHighlightedVirtualNodes() {
    // TODO Auto-generated method stub
    return Collections.emptyList();
  }

  @Override
  public Collection<WaySegment> getHighlightedWaySegments() {
    // TODO Auto-generated method stub
    return Collections.emptyList();
  }

  @Override
  public void setHighlightedVirtualNodes(Collection<WaySegment> waySegments) {
    // TODO Auto-generated method stub

  }

  @Override
  public void setHighlightedWaySegments(Collection<WaySegment> waySegments) {
    // TODO Auto-generated method stub

  }

  @Override
  public void addHighlightUpdateListener(HighlightUpdateListener listener) {
    // TODO Auto-generated method stub

  }

  @Override
  public void removeHighlightUpdateListener(HighlightUpdateListener listener) {
    // TODO Auto-generated method stub

  }

  @Override
  public Collection<MapillaryPrimitive> getAllSelected() {
    return Collections.unmodifiableCollection(selectedPrimitives);
  }

  @Override
  public boolean selectionEmpty() {
    return selectedPrimitives.isEmpty();
  }

  @Override
  public boolean isSelected(MapillaryPrimitive osm) {
    return selectedPrimitives.contains(osm);
  }

  @Override
  public void toggleSelected(Collection<? extends PrimitiveId> osm) {
    List<MapillaryPrimitive> realPrimitives = osm.stream().map(this::getPrimitiveById).filter(Objects::nonNull)
        .collect(Collectors.toList());
    realPrimitives.stream().filter(p -> !selectedPrimitives.remove(p)).forEach(this::addSelected);
  }

  @Override
  public void toggleSelected(PrimitiveId... osm) {
    toggleSelected(Arrays.asList(osm));
  }

  @Override
  public void setSelected(Collection<? extends PrimitiveId> selection) {
    selectedPrimitives.clear();
    selection.stream().map(this::getPrimitiveById).forEach(selectedPrimitives::add);
    if (!selectedPrimitives.isEmpty()) {
      MapillaryPrimitive p = selectedPrimitives.iterator().next();
      if (p instanceof MapillaryAbstractImage) {
        this.setSelectedImage((MapillaryAbstractImage) p, true);
      }
    }
  }

  @Override
  public void setSelected(PrimitiveId... osm) {
    setSelected(Arrays.asList(osm));
  }

  @Override
  public void addSelected(Collection<? extends PrimitiveId> selection) {
    selection.stream().map(this::getPrimitiveById).forEach(selectedPrimitives::add);
  }

  @Override
  public void addSelected(PrimitiveId... osm) {
    addSelected(Arrays.asList(osm));
  }

  @Override
  public void clearSelection(PrimitiveId... osm) {
    clearSelection(Arrays.asList(osm));
  }

  @Override
  public void clearSelection(Collection<? extends PrimitiveId> list) {
    list.stream().map(this::getPrimitiveById).forEach(selectedPrimitives::remove);
  }

  @Override
  public void clearSelection() {
    selectedPrimitives.clear();
  }

  @Override
  public void addSelectionListener(DataSelectionListener listener) {
    // TODO Auto-generated method stub

  }

  @Override
  public void removeSelectionListener(DataSelectionListener listener) {
    // TODO Auto-generated method stub

  }

  @Override
  public void clearMappaintCache() {
    mappaintCacheIdx++;
  }

  /**
   * Returns mappaint cache index for this DataSet.
   *
   * If the {@link OsmPrimitive#mappaintCacheIdx} is not equal to the DataSet mappaint cache index, this means the cache
   * for that primitive is out of date.
   *
   * @return mappaint cache index
   */
  public short getMappaintCacheIndex() {
    return mappaintCacheIdx;
  }
}
