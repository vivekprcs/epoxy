package com.airbnb.epoxy;

import android.content.Context;
import android.support.annotation.Nullable;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.util.SparseArray;
import android.view.ViewGroup;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

public class EpoxyRecyclerView extends RecyclerView {

  static final List<PoolReference> RECYCLER_POOLS = new ArrayList<>(5);

  private EpoxyController epoxyController;

  public EpoxyRecyclerView(Context context) {
    super(context);
    initViewPool();
  }

  public EpoxyRecyclerView(Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
    initViewPool();
  }

  public EpoxyRecyclerView(Context context, @Nullable AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
    initViewPool();
  }

  private void initViewPool() {
    if (!isAutoSharingViewPoolAcrossContext()) {
      setRecycledViewPool(createViewPool());
      return;
    }

    Context context = getContext();
    Iterator<PoolReference> iterator = RECYCLER_POOLS.iterator();
    PoolReference poolToUse = null;

    while (iterator.hasNext()) {
      PoolReference poolReference = iterator.next();
      if (poolReference.context() == null) {
        // Clean up entries from old activities so the list doesn't grow large
        iterator.remove();
      } else if (poolReference.context() == context) {
        if (poolToUse != null) {
          throw new IllegalStateException("A pool was already found");
        }
        poolToUse = poolReference;
        // finish iterating to remove any old contexts
      }
    }

    if (poolToUse == null) {
      poolToUse = new PoolReference(context, createViewPool());
      RECYCLER_POOLS.add(poolToUse);
    }

    setRecycledViewPool(poolToUse.viewPool);
  }


  protected RecycledViewPool createViewPool() {
    return new UnboundedViewPool();
  }

  public boolean isAutoSharingViewPoolAcrossContext() {
    return true;
  }


  @Override
  public void setLayoutParams(ViewGroup.LayoutParams params) {
    boolean isFirstParams = getLayoutParams() == null;
    super.setLayoutParams(params);

    if (isFirstParams) {
      // Set a default layout manager if one was not set via xml
      // We need layout params for this to guess at the right size and type
      if (getLayoutManager() == null) {
        setLayoutManager(createLayoutManager());
      }
    }
  }

  protected LayoutManager createLayoutManager() {
    ViewGroup.LayoutParams layoutParams = getLayoutParams();

    if (layoutParams.height == LayoutParams.MATCH_PARENT
        // 0 represents matching constraints in a LinearLayout or ConstraintLayout
        || layoutParams.height == 0) {

      if (layoutParams.width == LayoutParams.MATCH_PARENT
          || layoutParams.width == 0) {
        // If we are filling as much space as possible then we usually are fixed size
        setHasFixedSize(true);
      }

      // A sane default is a vertically scrolling linear layout
      return new LinearLayoutManager(getContext());
    } else {
      // We are assuming this is the "carousel" case - a nested horizontal scrolling view.

      // Carousels generally go edge to edge. Clip to padding is turned off
      // so the previous and next views in the list "peek" from the edges.
      setClipToPadding(false);

      // This is usually the case for horizontally scrolling carousels and should be a sane
      // default
      return new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false);
    }
  }


  @Override
  public void setLayoutManager(LayoutManager layout) {
    super.setLayoutManager(layout);
    syncSpanCount();
  }

  /**
   * If a grid layout manager is set we sync the span count between the layout and the epoxy
   * adapter.
   */
  private void syncSpanCount() {
    LayoutManager layoutManager = getLayoutManager();
    if (layoutManager instanceof GridLayoutManager && epoxyController != null) {
      epoxyController.setSpanCount(((GridLayoutManager) layoutManager).getSpanCount());
      ((GridLayoutManager) layoutManager).setSpanSizeLookup(epoxyController.getSpanSizeLookup());
    }
  }

  public void setModels(List<? extends EpoxyModel<?>> models) {
    if (!(epoxyController instanceof SimpleEpoxyController)) {
      setEpoxyController(new SimpleEpoxyController());
    }

    ((SimpleEpoxyController) epoxyController).setModels(models);
  }

  public void clear() {
    if (epoxyController == null) {
      return;
    }

    // The controller is cleared so the next time models are set we can create a fresh one.
    epoxyController.cancelPendingModelBuild();
    epoxyController = null;

    // We use swapAdapter instead of setAdapter so that the view pool is not cleared.
    // 'removeAndRecycleExistingViews=true' is used in case this is a nested recyclerview
    // and we want to recycle the views back to a shared view pool
    swapAdapter(null, true);
  }

  /**
   * Set an EpoxyController to populate this RecyclerView. This does not make the controller build
   * its models, that must be done separately via {@link #requestModelBuild()}.
   * <p>
   * Use this if you don't want {@link #requestModelBuild()} called automatically. Common cases
   * are
   * if you are using {@link TypedEpoxyController} (in which case you must call setData on the
   * controller), or if you have not otherwise populated your controllers data yet.
   * <p>
   * Otherwise if you want models built automatically for you use {@link
   * #setEpoxyControllerAndBuildModels(EpoxyController)}
   *
   * @see #setEpoxyControllerAndBuildModels(EpoxyController)
   */

  public void setEpoxyController(EpoxyController controller) {
    epoxyController = controller;
    setAdapter(controller.getAdapter());
    syncSpanCount();
  }

  /**
   * Set an EpoxyController to populate this RecyclerView, and tell the controller to build
   * models.
   */
  public void setEpoxyControllerAndBuildModels(EpoxyController controller) {
    controller.requestModelBuild();
    setEpoxyController(controller);
  }

  public interface ModelBuilderCallback {
    void buildModels(EpoxyController controller);
  }

  /**
   * Allows you to build models via a callback instead of needing to create a new EpoxyController
   * class. This is useful if your models are simple and you would like to simply declare them in
   * your activity/fragment.
   * <p>
   * Another useful pattern is having your Activity or Fragment implement {@link
   * ModelBuilderCallback}.
   */
  public void buildModelsWith(final ModelBuilderCallback callback) {
    setEpoxyControllerAndBuildModels(new EpoxyController() {
      @Override
      protected void buildModels() {
        callback.buildModels(this);
      }
    });
  }

  /**
   * Request that the currently set EpoxyController has its models rebuilt. You can use this to
   * avoid saving your controller as a field.
   * <p>
   * You cannot use this if you the controller is a {@link TypedEpoxyController}. In that case
   * you must set data directly on the controller.
   */
  public void requestModelBuild() {
    if (epoxyController == null) {
      throw new IllegalStateException("A controller must be set before requesting a model build.");
    }

    epoxyController.requestModelBuild();
  }

  private static class PoolReference {
    private final WeakReference<Context> contextReference;
    private final RecycledViewPool viewPool;

    private PoolReference(Context context,
        RecycledViewPool viewPool) {
      this.contextReference = new WeakReference<>(context);
      this.viewPool = viewPool;
    }

    @Nullable
    private Context context() {
      return contextReference.get();
    }
  }

  /**
   * Like its parent, UnboundedViewPool lets you share Views between multiple RecyclerViews. However
   * there is no maximum number of recycled views that it will store. This usually ends up being
   * optimal, barring any hard memory constraints, as RecyclerViews do not recycle more Views than
   * they need.
   */
  private static class UnboundedViewPool extends RecycledViewPool {

    private final SparseArray<Queue<ViewHolder>> scrapHeaps = new SparseArray<>();

    @Override
    public void clear() {
      scrapHeaps.clear();
    }

    @Override
    public void setMaxRecycledViews(int viewType, int max) {
      throw new UnsupportedOperationException(
          "UnboundedViewPool does not support setting a maximum number of recycled views");
    }

    @Override
    @Nullable
    public ViewHolder getRecycledView(int viewType) {
      final Queue<ViewHolder> scrapHeap = scrapHeaps.get(viewType);
      return scrapHeap != null ? scrapHeap.poll() : null;
    }

    @Override
    public void putRecycledView(ViewHolder viewHolder) {
      getScrapHeapForType(viewHolder.getItemViewType()).add(viewHolder);
    }

    private Queue<ViewHolder> getScrapHeapForType(int viewType) {
      Queue<ViewHolder> scrapHeap = scrapHeaps.get(viewType);
      if (scrapHeap == null) {
        scrapHeap = new LinkedList<>();
        scrapHeaps.put(viewType, scrapHeap);
      }
      return scrapHeap;
    }
  }
}
