val my: Int = 1
    get() {
        <!VAL_REASSIGNMENT_VIA_BACKING_FIELD_WARNING!>field<!>++
        return field
    }