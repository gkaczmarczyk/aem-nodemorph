jQuery(function($) {

    const savedTabId = localStorage.getItem('nodemorph_selectedTab')
    if (savedTabId && (savedTabId === 'search-tab' || savedTabId === 'update-tab')) {
        Coral.commons.ready('#' + savedTabId, function(tabBtn) {
            $('#' + savedTabId).click()
        })
    }

    $('#search-tab, #update-tab').on('click', function() {
        localStorage.setItem('nodemorph_selectedTab', this.id)
    })

})
