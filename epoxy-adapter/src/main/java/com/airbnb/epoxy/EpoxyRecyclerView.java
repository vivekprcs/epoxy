package com.airbnb.epoxy;

import android.content.Context;
import android.support.annotation.DimenRes;
import android.support.annotation.Dimension;
import android.support.annotation.Nullable;
import android.support.annotation.Px;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.ViewGroup;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

/**
 * <i>This feature is in Beta - please report bugs, feature requests, or other feedback at
 * https://github.com/airbnb/epoxy by creating a new issue. Thanks!</i>
 * <p>
 * A RecyclerView implementation that makes for easier integration with Epoxy. The goal of this
 * class is to reduce boilerplate in setting up a RecyclerView by applying common defaults.
 * Additionally, several performance optimizations are made.
 * <p>
 * Improvements in this class are:
 * <p>
 * 1. A single view pool is automatically shared between all {@link EpoxyRecyclerView} instances in
 * the same activity. This should increase view recycling potential and increase performance. See
 * {@link #initViewPool()}.
 * <p>
 * 2. A layout manager is automatically added with sane defaults. See {@link
 * #createLayoutManager()}
 * <p>
 * 3. Fixed size is enabled if this view's size is MATCH_PARENT
 * <p>
 * 4. If a {@link GridLayoutManager} is used this will automatically sync the span count with the
 * {@link EpoxyController}. See {@link #syncSpanCount()}
 * <p>
 * 5. Helper methods like {@link #setModels(List)}, {@link #buildModelsWith(ModelBuilderCallback)}
 * make it simpler to set up simple RecyclerViews.
 * <p>
 * 6. Set an EpoxyController and build models in one step -
 * {@link #setControllerAndBuildModels(EpoxyController)}
 * (EpoxyController)}
 * <p>
 * 7. Defaults for usage as a nested recyclerview are provided in {@link Carousel}.
 */
public class EpoxyRecyclerView extends RecyclerView {

  private static final List<PoolReference> RECYCLER_POOLS = new ArrayList<>(5);

  private static boolean addPaddingInScrollDirectionByDefault = true;
  @Dimension(unit = Dimension.DP) private static int defaultPaddingInScrollDirectionDp = 8;

  private static boolean enableItemSpacingByDefault = true;
  @Dimension(unit = Dimension.DP)
  private static int defaultSpacingBetweenItemsDp = defaultPaddingInScrollDirectionDp;
  private boolean itemSpacingEnabled = enableItemSpacingByDefault;
  private boolean hasSetCustomItemSpacingAmount;
  protected final EpoxyItemSpacingDecorator spacingDecorator = new EpoxyItemSpacingDecorator();

  private EpoxyController epoxyController;

  public EpoxyRecyclerView(Context context) {
    super(context);
    init();
  }

  public EpoxyRecyclerView(Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
    init();
  }

  public EpoxyRecyclerView(Context context, @Nullable AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
    init();
  }

  private void init() {
    setClipToPadding(false);
    if (addPaddingInScrollDirectionByDefault) {
      setPaddingInScrollDirectionDp(defaultPaddingInScrollDirectionDp);
    }

    setItemSpacingDp(defaultSpacingBetweenItemsDp);
    hasSetCustomItemSpacingAmount = false;
    if (itemSpacingEnabled) {
      addItemDecoration(spacingDecorator);
    }

    initViewPool();
  }

  /**
   * Get or create a view pool to use for this RecyclerView. By default the same pool is shared for
   * all {@link EpoxyRecyclerView} usages in the same Activity.
   *
   * @see #createViewPool()
   * @see #isAutoSharingViewPoolAcrossContext()
   */
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

  /**
   * Create a new instance of a view pool to use with this recyclerview. By default a {@link
   * UnboundedViewPool} is used.
   */
  protected RecycledViewPool createViewPool() {
    return new UnboundedViewPool();
  }

  /**
   * To maximize view recycling by default we share the same view pool across all {@link
   * EpoxyRecyclerView} instances in the same Activity. This behavior can be disabled by returning
   * false here.
   */
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

  /**
   * Create a new {@link android.support.v7.widget.RecyclerView.LayoutManager} instance to use for
   * this RecyclerView.
   * <p>
   * By default a LinearLayoutManager is used, and a reasonable default is chosen for scrolling
   * direction based on layout params.
   * <p>
   * If the RecyclerView is set to match parent size then the scrolling orientation is set to
   * vertical and {@link #setHasFixedSize(boolean)} is set to true.
   * <p>
   * If the height is set to wrap_content then the scrolling orientation is set to horizontal, and
   * {@link #setClipToPadding(boolean)} is set to false.
   */
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
   * adapter automatically.
   */
  private void syncSpanCount() {
    LayoutManager layout = getLayoutManager();
    if (layout instanceof GridLayoutManager && epoxyController != null) {
      GridLayoutManager grid = (GridLayoutManager) layout;

      if (epoxyController.getSpanCount() != grid.getSpanCount()
          || grid.getSpanSizeLookup() != epoxyController.getSpanSizeLookup()) {
        epoxyController.setSpanCount(grid.getSpanCount());
        grid.setSpanSizeLookup(epoxyController.getSpanSizeLookup());
      }
    }
  }

  @Override
  public void requestLayout() {
    // Grid layout manager calls this when the span count is changed. Its the easiest way to
    // detect a span change and update our controller accordingly.
    syncSpanCount();
    super.requestLayout();
  }

  public static void addPaddingInScrollDirectionByDefault(boolean addPaddingInScrollDirection) {
    addPaddingInScrollDirectionByDefault = addPaddingInScrollDirection;
  }

  /**
   * Set a DP value to use as the default padding value in the scroll direction of every
   * RecyclerView.
   * <p>
   * The default is {@link #defaultPaddingInScrollDirectionDp}
   */
  public static void setDefaultPaddingInScrollDirection(
      @Dimension(unit = Dimension.DP) int paddingDp) {
    defaultPaddingInScrollDirectionDp = paddingDp;
  }

  /**
   * Set a dimension resource to use as the padding value in the scrolling direction of this
   * RecyclerView.
   *
   * @see #setPaddingInScrollDirectionPx(int)
   */
  public void setPaddingInScrollDirectionRes(@DimenRes int paddingRes) {
    setPaddingInScrollDirectionPx(getResources().getDimensionPixelOffset(paddingRes));
  }

  /**
   * Set a DP amount to use as the padding value in the scrolling direction of this
   * RecyclerView.
   *
   * @see #setPaddingInScrollDirectionPx(int)
   */
  public void setPaddingInScrollDirectionDp(@Dimension(unit = Dimension.DP) int paddingDp) {
    setPaddingInScrollDirectionPx(dpToPx(paddingDp));
  }

  /**
   * Set a pixel amount to use as padding in the scrolling direction of this
   * RecyclerView.
   * <p>
   * For example, if this is scrolling vertically then the top and bottom padding will be set. If
   * horizontally, the left and right padding will be set.
   * <p>
   * The default is {@link #defaultPaddingInScrollDirectionDp} dp.
   * <p>
   * Alternatively you can specify a custom global default with {@link
   * #setDefaultPaddingInScrollDirection(int)}
   */
  public void setPaddingInScrollDirectionPx(@Px int paddingPx) {
    if (getLayoutManager().canScrollHorizontally()) {
      setPadding(paddingPx, getPaddingTop(), paddingPx, getPaddingBottom());
    } else {
      setPadding(getPaddingLeft(), paddingPx, getPaddingRight(), paddingPx);
    }
    if (!hasSetCustomItemSpacingAmount && paddingPx > 0) {
      // Default to using the same spacing between items as padding on edges
      setItemSpacingPx(paddingPx);
      hasSetCustomItemSpacingAmount = false;
    }
  }

  public static void setItemSpacingEnabledByDefault(boolean enableItemSpacingByDefault) {
    EpoxyRecyclerView.enableItemSpacingByDefault = enableItemSpacingByDefault;
  }

  public void enableItemSpacing(boolean enableItemSpacing) {
    if (this.itemSpacingEnabled == enableItemSpacing) {
      return;
    }

    this.itemSpacingEnabled = enableItemSpacing;
    if (enableItemSpacing) {
      addItemDecoration(spacingDecorator);
    } else {
      removeItemDecoration(spacingDecorator);
    }
  }

  public static void setDefaultItemSpacingDp(@Dimension(unit = Dimension.DP) int dp) {
    defaultSpacingBetweenItemsDp = dp;
  }

  public void setItemSpacingRes(@DimenRes int itemSpacingRes) {
    setItemSpacingPx(getResources().getDimensionPixelOffset(itemSpacingRes));
  }

  public void setItemSpacingDp(@Dimension(unit = Dimension.DP) int dp) {
    setItemSpacingPx(dpToPx(dp));
  }

  public void setItemSpacingPx(@Px int spacingPx) {
    boolean valueChanged = spacingDecorator.setPxBetweenItems(spacingPx);
    hasSetCustomItemSpacingAmount = true;
    if (valueChanged && itemSpacingEnabled) {
      invalidateItemDecorations();
    }
  }

  /**
   * Set a list of {@link EpoxyModel}'s to show in this RecyclerView.
   * <p>
   * Alternatively you can set an {@link EpoxyController} to handle building models dynamically.
   *
   * @see #setController(EpoxyController)
   * @see #setControllerAndBuildModels(EpoxyController)
   * @see #buildModelsWith(ModelBuilderCallback)
   */

  public void setModels(List<? extends EpoxyModel<?>> models) {
    if (!(epoxyController instanceof SimpleEpoxyController)) {
      setController(new SimpleEpoxyController());
    }

    ((SimpleEpoxyController) epoxyController).setModels(models);
  }

  /**
   * Set an EpoxyController to populate this RecyclerView. This does not make the controller build
   * its models, that must be done separately via {@link #requestModelBuild()}.
   * <p>
   * Use this if you don't want {@link #requestModelBuild()} called automatically. Common cases
   * are if you are using {@link TypedEpoxyController} (in which case you must call setData on the
   * controller), or if you have not otherwise populated your controller's data yet.
   * <p>
   * Otherwise if you want models built automatically for you use {@link
   * #setControllerAndBuildModels(EpoxyController)}
   *
   * @see #setControllerAndBuildModels(EpoxyController)
   * @see #buildModelsWith(ModelBuilderCallback)
   * @see #setModels(List)
   */

  public void setController(EpoxyController controller) {
    epoxyController = controller;
    setAdapter(controller.getAdapter());
    syncSpanCount();
  }

  /**
   * Set an EpoxyController to populate this RecyclerView, and tell the controller to build
   * models.
   *
   * @see #setController(EpoxyController)
   * @see #buildModelsWith(ModelBuilderCallback)
   * @see #setModels(List)
   */
  public void setControllerAndBuildModels(EpoxyController controller) {
    controller.requestModelBuild();
    setController(controller);
  }

  /**
   * Allows you to build models via a callback instead of needing to create a new EpoxyController
   * class. This is useful if your models are simple and you would like to simply declare them in
   * your activity/fragment.
   * <p>
   * Another useful pattern is having your Activity or Fragment implement {@link
   * ModelBuilderCallback}.
   *
   * @see #setController(EpoxyController)
   * @see #setControllerAndBuildModels(EpoxyController)
   * @see #setModels(List)
   */
  public void buildModelsWith(final ModelBuilderCallback callback) {
    setControllerAndBuildModels(new EpoxyController() {
      @Override
      protected void buildModels() {
        callback.buildModels(this);
      }
    });
  }

  /**
   * A callback for creating models without needing a custom EpoxyController class. Used with {@link
   * #buildModelsWith(ModelBuilderCallback)}
   */
  public interface ModelBuilderCallback {
    /**
     * Analagous to {@link EpoxyController#buildModels()}. You should create new model instances and
     * add them to the given controller. {@link AutoModel} cannot be used with models added this
     * way.
     */
    void buildModels(EpoxyController controller);
  }

  /**
   * Request that the currently set EpoxyController has its models rebuilt. You can use this to
   * avoid saving your controller as a field.
   * <p>
   * You cannot use this if your controller is a {@link TypedEpoxyController} or if you set
   * models via {@link #setModels(List)}. In that case you must set data directly on the
   * controller or set models again.
   */
  public void requestModelBuild() {
    if (epoxyController == null) {
      throw new IllegalStateException("A controller must be set before requesting a model build.");
    }

    if (epoxyController instanceof SimpleEpoxyController) {
      throw new IllegalStateException("Models were set with #setModels, they can not be rebuilt.");
    }

    epoxyController.requestModelBuild();
  }

  /**
   * Clear the currently set EpoxyController as well as any models that are displayed.
   * <p>
   * Any pending requests to the EpoxyController to build models are canceled.
   * <p>
   * Any existing child views are recycled to the view pool.
   */
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

  @Px
  private int dpToPx(@Dimension(unit = Dimension.DP) int dp) {
    return (int) TypedValue
        .applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp,
            getResources().getDisplayMetrics());
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
