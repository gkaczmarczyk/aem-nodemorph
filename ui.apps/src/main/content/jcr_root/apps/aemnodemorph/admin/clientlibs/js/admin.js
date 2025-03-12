(function($, Coral) {
    "use strict";

    const PN_JCR_PATH = 'jcr:path'
    const PN_JCR_PRIMARY_TYPE = 'jcr:primaryType'
    const PN_JCR_TITLE = 'jcr:title'
    const PN_SLING_RESOURCETYPE = 'sling:resourceType'
    const PN_CQ_LAST_MODIFIED = 'cq:lastModified'

    document.addEventListener("DOMContentLoaded", (event) => {
        if (typeof Coral !== 'undefined') {
            Coral.commons.ready(() => init())
        } else {
            document.addEventListener('coral-component:attached', function handler() {
                Coral.commons.ready(() => init())
                document.removeEventListener('coral-component:attached', handler); // Run once
            })
        }
    })

    function init() {
        let lastSearchResults = []
        const $matchPropCheckbox = $('#matchProperty');
        const $propNameWrapper = $('#property-name-wrapper');
        const $queryField = $('#query-field');
        const $queryLabel = $('#query-label');

        // Toggle Property Name Field and Relabel Query
        $matchPropCheckbox.on('change', function() {
            const isChecked = $(this).prop('checked');
            $propNameWrapper.toggleClass('visible', isChecked);
            $queryLabel.text(isChecked ? 'Property Value' : 'Search for Node');
            $queryField.attr('placeholder', isChecked ? 'Enter property value' : 'Enter node name (e.g., mynode_*)');
        })

        // Search Form Submission
        $('#nodemorph-search-form').on('submit', function(e) {
            e.preventDefault();
            const path = $pathInput.val();
            const query = $queryField.val().trim();
            const matchProp = $matchPropCheckbox.prop('checked');
            const propName = $('#property-name-field').val();
            const pageOnly = $("coral-checkbox[name='pageOnly']").prop('checked');
            const verbose = $("coral-checkbox[name='verbose']").prop('checked');
            let url = '/bin/querybuilder.json?path=' + encodeURIComponent(path) + '&p.limit=1000&p.hits=full';
            url += '&p.hits=full&p.nodedepth=0';

            if (pageOnly) {
                url += '&type=cq:Page'
            }
            if (query) {
                if (matchProp && propName) {
                    url += '&property=' + encodeURIComponent(propName) + '&property.value=' + encodeURIComponent(query)
                } else {
                    url += '&nodename=' + encodeURIComponent(query)
                }
            }

            $.getJSON(url, function(data) {
                const tbody = $('#nodemorph-search-results .coral-Table-body').empty();
                const resultCount = data.results;
                $('#result-text').text(`Found ${resultCount} result${resultCount === 1 ? '' : 's'}`)
                lastSearchResults = data.hits
                $('#export-csv-btn').css('display', resultCount > 0 ? 'inline-block' : 'none')
                $('#nodemorph-search-results').css('display', resultCount > 0 ? 'table' : 'none')

                if (data.hits && data.hits.length > 0) {
                    data.hits.forEach(function(hit) {
                        const row = document.createElement('tr');
                        row.className = 'coral-Table-row';
                        row.innerHTML = `
                            <td class='coral-Table-cell'>${hit[PN_JCR_PATH]}</td>
                            <td class='coral-Table-cell'>${hit[PN_JCR_TITLE] || '-'}</td>
                            <td class='coral-Table-cell'>${matchProp && propName ? hit[propName] || 'N/A' : hit[PN_JCR_PRIMARY_TYPE]}</td>
                        `;
                        if (verbose) {
                            const props = Object.entries(hit)
                                .filter(([key]) => !['path', 'name', PN_JCR_PRIMARY_TYPE].includes(key))
                                .map(([key, val]) => `${key}: ${val}`)
                                .join('\n');
                            row.setAttribute('title', props);
                        }
                        tbody.append(row);
                    });
                } else {
                    tbody.innerHTML = '<tr class="coral-Table-row"><td class="coral-Table-cell" colspan="3">No results found</td></tr>';
                }
            }).fail(function(xhr) {
                $('#result-text').text('Search failed')
                $('#export-csv-btn').css('display', 'none')
                $('#nodemorph-search-results').css('display', 'none')
                new Coral.Alert().set({
                    variant: 'error',
                    header: 'Search Failed',
                    content: { innerHTML: 'Check path or server logs: ' + xhr.statusText }
                }).display();
            });
        });

        // Download Search results CSV
        $('#export-csv-btn').on('click', function() {
            if (lastSearchResults.length === 0) return

            const headers = ['Path', 'Title', 'Matched Value']
            const csvRows = [headers.join(',')]

            lastSearchResults.forEach(hit => {
                const matchProp = $matchPropCheckbox.prop('checked')
                const propName = $('#property-name-field').val()
                const row = [
                    `"${hit[PN_JCR_PATH]}"`,
                    `"${hit[PN_JCR_TITLE] || '-'}"`,
                    `"${matchProp && propName ? hit[propName] || 'N/A' : hit[PN_JCR_PRIMARY_TYPE]}"`
                ]
                csvRows.push(row.join(','))
            })

            const csvContent = csvRows.join('\n')
            const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' })
            const link = document.createElement('a')
            const url = URL.createObjectURL(blob)
            link.setAttribute('href', url)
            link.setAttribute('download', 'nodemorph-search-results.csv')
            document.body.appendChild(link)
            link.click()
            document.body.removeChild(link)
            URL.revokeObjectURL(url)
        });

        const $operationSelect = $('#update-operation')
        const $matchTypeSelect = $('#match-type')
        const $operationFields = $('.operation-fields')

        $operationSelect.on('change', function() {
            const operation = $(this).val()
            $operationFields.removeClass('visible')

            if (operation === 'add') {
                $('#match-type-fields').addClass('visible')
                $('#add-fields').addClass('visible')
                $matchTypeSelect.trigger('change')
            } else if (operation === 'copy') {
                $('#copy-type-fields').addClass('visible')
                $('#copy-detail-fields').addClass('visible')
            } else {
                $(`#${operation}-fields`).addClass('visible')
            }
        })

        $matchTypeSelect.on('change', function() {
            const matchType = $(this).val();
            const $addFields = $('#add-fields')
            const $propFields = $addFields.find('.match-property-fields')
            const $nodeFields = $addFields.find('.match-node-fields')

            if (matchType === 'property') {
                $propFields.addClass('visible')
                $nodeFields.removeClass('visible')
            } else {
                $propFields.removeClass('visible')
                $nodeFields.addClass('visible')
            }
        })

        $('#add-prop').on('click', function(e) {
            e.preventDefault()
            const $list = $('.property-list')
            const $row = $('<div class="property-row">' +
                '<input is="coral-textfield" name="properties[]" placeholder="key=value" />' +
                '<button is="coral-button" class="remove-prop" variant="quiet" icon="delete" iconsize="S"></button>' +
                '</div>')
            $list.append($row)
            $list.find('.remove-prop').show() // Show delete on all rows if > 1
            const $addFields = $('#add-fields')
            if ($addFields.hasClass('visible')) {
                $addFields.css('max-height', $addFields[0].scrollHeight + 'px')
            }
        })

        $(document).on('click', '.remove-prop', function(e) {
            e.preventDefault()
            const $row = $(this).closest('.property-row')
            $row.remove()
            if ($('.property-row').length === 1) {
                $('.remove-prop').hide()
            }
            const $addFields = $('#add-fields')
            if ($addFields.hasClass('visible')) {
                $addFields.css('max-height', $addFields[0].scrollHeight + 'px')
            }
        })

        if ($('.property-row').length === 1) {
            $('.remove-prop').hide();
        }

        // Update Form Submission
        $('#nodemorph-update-form').on('submit', function(e) {
            e.preventDefault();
            const formData = {
                path: $('#update-path').val(),
                operation: $('#update-operation').val(),
                pageOnly: $('coral-checkbox[name="pageOnly"]').prop('checked'),
                dryRun: $('coral-checkbox[name="dryRun"]').prop('checked'),
            }

            switch (formData.operation) {
                case 'add':
                    formData.matchType = $matchTypeSelect.val()
                    if (formData.matchType === 'property') {
                        formData.ifProp = $('input[name="ifProp"]').val()
                        formData.ifValue = $('input[name="ifValue"]').val()
                    } else {
                        formData.nodeName = $('input[name="nodeName"]').val()
                    }
                    formData.properties = $('input[name="properties[]"]').map(function() { return $(this).val(); }).get().join('\n')
                    break
                case 'delete':
                    formData.propNames = $('input[name="propNames"]').val();
                    break;
                case 'replace':
                    formData.propName = $('input[name="propName"]').val();
                    formData.find = $('input[name="find"]').val();
                    formData.replace = $('input[name="replace"]').val();
                    break;
                case 'copy':
                    formData.copyType = $('#copy-type').val();
                    formData.source = $('input[name="source"]').val();
                    formData.target = $('input[name="target"]').val();
                    break;
            }

            $.post('/apps/aemnodemorph/admin/content/aemnodemorph.update.json', formData, function(data) {
                $('#update-result-text').text(`Affected ${data.total} nodes`);
                const tbody = $('#nodemorph-update-results .coral-Table-body').empty();
                $('#nodemorph-update-results').css('display', data.total > 0 ? 'table' : 'none')
                data.actions.forEach(action => {
                    const row = `<tr class="coral-Table-row">
                <td class="coral-Table-cell">${action.path}</td>
                <td class="coral-Table-cell">${action.action}</td>
                <td class="coral-Table-cell">${action.status}</td>
            </tr>`;
                    tbody.append(row);
                });
            }).fail(function(xhr) {
                $('#update-result-text').text('Update failed')
                // $('#export-csv-btn').css('display', 'none')
                $('#nodemorph-update-results').css('display', 'none')
                new Coral.Alert().set({
                    variant: 'error', header: 'Update Failed', content: { innerHTML: xhr.statusText }
                }).display()
            });
        })

        // Update Tab Functionality
        $('#nodemorph-preview-btn').on('click', function() {
            const path = $("#nodemorph-update-form [name='path']").val();
            const prop = $("#nodemorph-update-form [name='property']").val();
            const value = $("#nodemorph-update-form [name='value']").val();
            $('#preview-text').text(`Set ${path}/${prop} to "${value}"`);
            $('#nodemorph-update-preview').show();
        });

        if ($operationSelect[0].hasAttribute('is') && $matchTypeSelect[0].hasAttribute('is')) {
            $operationSelect.trigger('change')
        } else {
            $operationSelect.one('coral-component:attached', () => {
                $matchTypeSelect.one('coral-component:attached', () => {
                    $operationSelect.trigger('change')
                });
            });
        }

    }
})(jQuery, Coral)
