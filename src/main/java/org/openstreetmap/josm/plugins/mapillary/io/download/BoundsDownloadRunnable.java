// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapillary.io.download;

import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.RecursiveAction;
import java.util.function.Function;

import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.Notification;
import org.openstreetmap.josm.gui.progress.AbstractProgressMonitor;
import org.openstreetmap.josm.gui.progress.ChildProgress;
import org.openstreetmap.josm.gui.progress.NullProgressMonitor;
import org.openstreetmap.josm.gui.progress.ProgressMonitor;
import org.openstreetmap.josm.gui.util.GuiHelper;
import org.openstreetmap.josm.plugins.mapillary.MapillaryPlugin;
import org.openstreetmap.josm.plugins.mapillary.gui.DownloadProgressMonitor;
import org.openstreetmap.josm.plugins.mapillary.oauth.MapillaryUser;
import org.openstreetmap.josm.plugins.mapillary.oauth.OAuthUtils;
import org.openstreetmap.josm.plugins.mapillary.utils.MapillaryProperties;
import org.openstreetmap.josm.plugins.mapillary.utils.MapillaryURL.APIv3;
import org.openstreetmap.josm.tools.HttpClient;
import org.openstreetmap.josm.tools.I18n;
import org.openstreetmap.josm.tools.ImageProvider.ImageSizes;
import org.openstreetmap.josm.tools.Logging;

public abstract class BoundsDownloadRunnable extends RecursiveAction {

  private static final long serialVersionUID = -3097850570397160069L;
  protected final Bounds bounds;
  protected final Collection<URL> urls;
  protected static final int MAXIMUM_URLS = 50;
  protected final ProgressMonitor monitor;
  private final Function<Bounds, Collection<URL>> urlGen;
  /**
   * Checks if this download has been completed before
   */
  protected boolean completed = Boolean.FALSE;

  public BoundsDownloadRunnable(final Bounds bounds, final Function<Bounds, Collection<URL>> urlGen,
    ProgressMonitor monitor) {
    this(bounds, urlGen, null, monitor);
  }

  public BoundsDownloadRunnable(final Bounds bounds, final Function<Bounds, Collection<URL>> urlGen,
    Collection<URL> urls, ProgressMonitor monitor) {
    this.bounds = bounds;
    this.urlGen = urlGen;
    this.urls = urls == null ? getUrlGenerator().apply(bounds) : urls;
    this.monitor = monitor;
  }

  public void run() {
    if (!completed) {
      for (URL url : urls) {
        realRun(url);
      }
    }
  }

  protected Function<Bounds, Collection<URL>> getUrlGenerator() {
    return this.urlGen;
  }

  private void realRun(URL currentUrl) {
    HttpClient client = null;
    try {
      if (Thread.interrupted()) {
        Logging.debug("{} for {} interrupted!", getClass().getSimpleName(), bounds.toString());
        return;
      }
      client = HttpClient.create(currentUrl);
      client.setReadTimeout(MapillaryProperties.TIMEOUT.get());
      client.setHeader("Accept-Encoding", null); // compression is broken as of 2020-03-03
      if (MapillaryUser.getUsername() != null) {
        OAuthUtils.addAuthenticationHeader(client);
      }
      // HttpClient sets the state to FINISHED, and the subsequent calls to getResponse().getBufferedReader() error out.
      HttpClient.Response response = client.connect(NullProgressMonitor.INSTANCE);
      monitor.beginTask("Download data");
      if (monitor instanceof ChildProgress) {
        AbstractProgressMonitor parentMonitor = ((ChildProgress) monitor).getParent();
        if (parentMonitor instanceof DownloadProgressMonitor) {
          DownloadProgressMonitor parentDownloadMonitor = (DownloadProgressMonitor) parentMonitor;
          parentDownloadMonitor.setSize(response.getContentLength());
        }
      }
      URL nextURL = APIv3.parseNextFromLinkHeaderValue(response.getHeaderField("Link"));
      if (nextURL != null) {
        ForkJoinPool pool = ForkJoinTask.getPool();
        if (pool != null)
          pool.execute(getNextUrl(nextURL));
        else
          MainApplication.worker.execute(() -> getNextUrl(nextURL).run());
      }
      run(client);
      if (monitor instanceof ChildProgress) {
        AbstractProgressMonitor parentMonitor = ((ChildProgress) monitor).getParent();
        if (parentMonitor instanceof DownloadProgressMonitor) {
          DownloadProgressMonitor parentDownloadMonitor = (DownloadProgressMonitor) parentMonitor;
          parentDownloadMonitor.updateCompleted();
        }
      }
      monitor.finishTask();
    } catch (IOException e) {
      client.disconnect();
      String message = I18n.tr("Could not read from URL {0}!", currentUrl.toString());
      Logging.log(Logging.LEVEL_WARN, message, e);
      if (!GraphicsEnvironment.isHeadless()) {
        GuiHelper.runInEDT(() -> showNotification(message));
      }
    }
  }

  protected static void showNotification(String message) {
    new Notification(message).setIcon(MapillaryPlugin.LOGO.setSize(ImageSizes.LARGEICON).get())
      .setDuration(Notification.TIME_SHORT).show();
  }

  /**
   * Logs information about the given connection via {@link Logging#info(String)}. If it's a {@link HttpURLConnection},
   * the request method, the response code and the URL itself are logged. Otherwise only the URL is logged.
   *
   * @param client the {@link URLConnection} for which information is logged
   * @param info an additional info text, which is appended to the output in braces
   */
  public static void logConnectionInfo(final HttpClient client, final String info) {
    final StringBuilder message = new StringBuilder(client.getRequestMethod()).append(' ').append(client.getURL())
      .append(" → ").append(client.getResponse().getResponseCode());

    if (info != null && info.length() >= 1) {
      message.append(" (").append(info).append(')');
    }
    Logging.info(message.toString());
  }

  public abstract void run(final HttpClient client) throws IOException;

  public abstract BoundsDownloadRunnable getNextUrl(final URL nextUrl);

  /**
   * Split the collection of URLs into reasonably sized chunks
   *
   * @return The URLs split into chunks for fork/join tasks.
   */
  protected List<Collection<URL>> splitUrls() {
    List<Collection<URL>> toExecute = new ArrayList<>();
    Collection<URL> collection = new ArrayList<>();
    toExecute.add(collection);
    for (URL url : urls) {
      if (!collection.isEmpty() && collection.size() % MAXIMUM_URLS == 0) {
        collection = new ArrayList<>();
        toExecute.add(collection);
      }
      collection.add(url);
    }
    return toExecute;
  }
}
