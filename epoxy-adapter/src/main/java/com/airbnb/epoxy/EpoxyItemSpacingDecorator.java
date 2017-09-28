package com.airbnb.epoxy;

import android.graphics.Rect;
import android.support.annotation.Px;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.GridLayoutManager.SpanSizeLookup;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.RecyclerView.ItemDecoration;
import android.support.v7.widget.RecyclerView.LayoutManager;
import android.support.v7.widget.RecyclerView.State;
import android.view.View;

/**
 * Modifies item spacing in a recycler view so that items are equally spaced no matter where they
 * are on the grid. Only designed to work with standard linear or grid layout managers.
 */
public class EpoxyItemSpacingDecorator extends ItemDecoration {
  private int innerPaddingPx;
  private boolean layoutReversed;
  private boolean verticallyScrolling;
  private boolean horizontallyScrolling;
  private int itemCount;
  private int position;
  private Rect outRect;

  public EpoxyItemSpacingDecorator() {
    this(0);
  }

  public EpoxyItemSpacingDecorator(@Px int pxBetweenItems) {
    setPxBetweenItems(pxBetweenItems);
  }

  public boolean setPxBetweenItems(@Px int pxBetweenItems) {
    int newSpacing = pxBetweenItems / 2;
    boolean changed = newSpacing != innerPaddingPx;
    innerPaddingPx = newSpacing;
    return changed;
  }

  @Px
  public int getPxBetweenItems() {
    return innerPaddingPx * 2;
  }

  @Override
  public void getItemOffsets(Rect outRect, View view, RecyclerView parent, State state) {
    // Zero everything out for the common case
    outRect.setEmpty();
    this.outRect = outRect;

    position = parent.getChildAdapterPosition(view);
    if (position == RecyclerView.NO_POSITION) {
      // View is not shown
      return;
    }

    itemCount = parent.getAdapter().getItemCount();
    LayoutManager layout = parent.getLayoutManager();
    horizontallyScrolling = layout.canScrollHorizontally();
    verticallyScrolling = layout.canScrollVertically();
    layoutReversed =
        layout instanceof LinearLayoutManager && ((LinearLayoutManager) layout).getReverseLayout();

    if (layout instanceof GridLayoutManager) {
      assignForGrid(((GridLayoutManager) layout));
    } else {
      assignForLinearLayout();
    }
  }

  private void assignForLinearLayout() {
    boolean firstItem = position == 0;
    boolean lastItem = position == itemCount - 1;

    // We assume there is just one row.
    // Does not support staggered grid or custom layout managers
    if (horizontallyScrolling) {
      if (firstItem) {
        if (layoutReversed) {
          outRect.left = innerPaddingPx;
        } else {
          outRect.right = innerPaddingPx;
        }
      } else if (lastItem) {
        if (layoutReversed) {
          outRect.right = innerPaddingPx;
        } else {
          outRect.left = innerPaddingPx;
        }
      } else {
        outRect.left = innerPaddingPx;
        outRect.right = innerPaddingPx;
      }
    }

    if (verticallyScrolling) {
      if (firstItem) {
        if (layoutReversed) {
          outRect.top = innerPaddingPx;
        } else {
          outRect.bottom = innerPaddingPx;
        }
      } else if (lastItem) {
        if (layoutReversed) {
          outRect.bottom = innerPaddingPx;
        } else {
          outRect.top = innerPaddingPx;
        }
      } else {
        outRect.top = innerPaddingPx;
        outRect.bottom = innerPaddingPx;
      }
    }
  }

  private void assignForGrid(GridLayoutManager layout) {
    final SpanSizeLookup spanSizeLookup = layout.getSpanSizeLookup();

    int spanSize = spanSizeLookup.getSpanSize(position);
    int spanCount = layout.getSpanCount();
    int spanIndex = spanSizeLookup.getSpanIndex(position, spanCount);

    int endSpanIndex = spanIndex + spanSize;
    boolean firstItemInRow = spanIndex == 0;
    boolean lastItemInRow = endSpanIndex == spanCount
        || position == itemCount - 1 // last item in list
        // next item doesn't fit in row
        || endSpanIndex + spanSizeLookup.getSpanSize(position + 1) > spanCount;
    boolean layoutReversed = layout.getReverseLayout();

    // From Gridlayoutmanager
    // Starting with RecyclerView <b>24.2.0</b>, span indices are always indexed from position 0
    // even if the layout is RTL. In a vertical GridLayoutManager, <b>leftmost</b> span is span
    // 0 if the layout is <b>LTR</b> and <b>rightmost</b> span is span 0 if the layout is
    // <b>RTL</b>. Prior to 24.2.0, it was the opposite

    if (firstItemInRow && lastItemInRow) {
      // Only item in row.
    } else if (firstItemInRow) {
      if (verticallyScrolling) {
        if (layoutReversed) {
          outRect.left = innerPaddingPx;
        } else {
          outRect.right = innerPaddingPx;
        }
      } else {
        if (layoutReversed) {
          outRect.top = innerPaddingPx;
        } else {
          outRect.bottom = innerPaddingPx;
        }
      }
    } else if (lastItemInRow) {
      // Last item in row
      if (verticallyScrolling) {
        if (layoutReversed) {
          outRect.right = innerPaddingPx;
        } else {
          outRect.left = innerPaddingPx;
        }
      } else {
        if (layoutReversed) {
          outRect.bottom = innerPaddingPx;
        } else {
          outRect.top = innerPaddingPx;
        }
      }
    } else {
      // Inner item (not relevant for less than three columns)
      if (verticallyScrolling) {
        outRect.left = innerPaddingPx;
        outRect.right = innerPaddingPx;
      } else {
        outRect.top = innerPaddingPx;
        outRect.bottom = innerPaddingPx;
      }
    }

    boolean isInFirstRow = isInFirstRow(position, spanSizeLookup, spanCount);
    boolean isInLastRow =
        !isInFirstRow && isInLastRow(position, itemCount, spanSizeLookup, spanCount);

    if (isInFirstRow) {
      if (verticallyScrolling) {
        if (layoutReversed) {
          outRect.top = innerPaddingPx;
        } else {
          outRect.bottom = innerPaddingPx;
        }
      } else {
        if (layoutReversed) {
          outRect.left = innerPaddingPx;
        } else {
          outRect.right = innerPaddingPx;
        }
      }
    } else if (isInLastRow) {
      if (verticallyScrolling) {
        if (layoutReversed) {
          outRect.bottom = innerPaddingPx;
        } else {
          outRect.top = innerPaddingPx;
        }
      } else {
        if (layoutReversed) {
          outRect.right = innerPaddingPx;
        } else {
          outRect.left = innerPaddingPx;
        }
      }
    } else {
      if (verticallyScrolling) {
        outRect.top = innerPaddingPx;
        outRect.bottom = innerPaddingPx;
      } else {
        outRect.left = innerPaddingPx;
        outRect.right = innerPaddingPx;
      }
    }
  }

  private static boolean isInFirstRow(int position, SpanSizeLookup spanSizeLookup, int spanCount) {
    int totalSpan = 0;
    for (int i = 0; i <= position; i++) {
      totalSpan += spanSizeLookup.getSpanSize(i);
      if (totalSpan > spanCount) {
        return false;
      }
    }

    return true;
  }

  private static boolean isInLastRow(int position, int itemCount, SpanSizeLookup spanSizeLookup,
      int spanCount) {
    int totalSpan = 0;
    for (int i = itemCount - 1; i >= position; i--) {
      totalSpan += spanSizeLookup.getSpanSize(i);
      if (totalSpan > spanCount) {
        return false;
      }
    }

    return true;
  }
}