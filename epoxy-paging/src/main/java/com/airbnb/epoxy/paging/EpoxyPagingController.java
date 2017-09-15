package com.airbnb.epoxy.paging;

import android.arch.paging.PagedList;
import android.arch.paging.PagedList.Callback;
import android.support.annotation.Nullable;

import com.airbnb.epoxy.EpoxyController;
import com.airbnb.epoxy.EpoxyModel;
import com.airbnb.epoxy.EpoxyViewHolder;

import java.util.Collections;
import java.util.List;

public abstract class EpoxyPagingController<T> extends EpoxyController {

  private PagedList<T> list;
  private List<T> snapshot = Collections.emptyList();

  @Override
  protected final void buildModels() {
    buildModels(snapshot);
  }

  protected abstract void buildModels(List<T> list);

  @Override
  protected void onModelBound(EpoxyViewHolder holder, EpoxyModel<?> boundModel, int position,
      @Nullable EpoxyModel<?> previouslyBoundModel) {
    list.loadAround(position);
  }

  public final void setList(PagedList<T> list) {
    if (list == this.list) {
      return;
    }

    PagedList<T> previousList = this.list;
    this.list = list;

    if (previousList != null) {
      previousList.removeWeakCallback(callback);
    }

    if (list != null) {
      list.addWeakCallback(previousList, callback);
    }

    if (previousList == null || list == null) {
      updateSnapshot();
    }
  }

  private final Callback callback = new Callback() {
    @Override
    public void onChanged(int position, int count) {
      updateSnapshot();
    }

    @Override
    public void onInserted(int position, int count) {
      updateSnapshot();
    }

    @Override
    public void onRemoved(int position, int count) {
      updateSnapshot();
    }
  };

  private void updateSnapshot() {
    snapshot = list == null ? Collections.<T>emptyList() : list.snapshot();
    requestModelBuild();
  }
}
