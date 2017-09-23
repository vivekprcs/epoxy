package com.airbnb.epoxy.test

import android.view.*
import com.airbnb.epoxy.*

@EpoxyModelClass
abstract class NewModelTest : EpoxyModel<View>() {

    @EpoxyAttribute
    var value: Int = 0

    override fun getDefaultLayout(): Int = R.layout.test_layout
}
