/*
 * Copyright © 2025 Gregory Kaczmarczyk
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
        const $substringMatchWrapper = $('#substring-match-wrapper')
        const $propNameWrapper = $('#property-name-wrapper');
        const $queryField = $('#query-field');
        const $queryLabel = $('#query-label');

        // Search tab: Toggle Property Name Field and Relabel Query
        $matchPropCheckbox.on('change', function() {
            const isChecked = $(this).prop('checked');
            $substringMatchWrapper.toggleClass('visible', isChecked)
            $propNameWrapper.toggleClass('visible', isChecked);
            $queryLabel.text(isChecked ? 'Property Value' : 'Search for Node');
            $queryField.attr('placeholder', isChecked ? 'Enter property value' : 'Enter node name (e.g. mynode_*)')
        })

        // Search Form Submission
        $('#nodemorph-search-form').on('submit', function(e) {
            e.preventDefault();
            $('#result-text').text('Searching...')
            $('#export-csv-btn').hide()
            $('#nodemorph-search-results').hide()
            $('#nodemorph-search-results .coral-Table-body').empty()
            $('#nodemorph-search-form button[type="submit"]').prop('disabled', true)
            const path = $('#nodepath').val()
            const query = $queryField.val().trim();
            if (!path || !query) {
                Coral.commons.toast('Please enter a valid path and query', {variant: 'error'})
                return
            }
            const matchProp = $matchPropCheckbox.prop('checked');
            const propName = $('#property-name-field').val();
            const substringMatch = $("coral-checkbox[name='substringMatch']").prop('checked')
            const pageOnly = $("coral-checkbox[name='pageOnly']").prop('checked');
            const verbose = $("coral-checkbox[name='verbose']").prop('checked');
            const propertiesInput = $('#properties-field').val()
            const properties = propertiesInput ? propertiesInput.split(',').map(p => p.trim()).filter(p => p) : [PN_JCR_TITLE, PN_JCR_PRIMARY_TYPE]
            let url = '/bin/querybuilder.json?path=' + encodeURIComponent(path) + '&p.limit=1000&p.hits=full&p.nodedepth=0'

            if (pageOnly) {
                url += '&type=cq:Page'
            }
            if (query) {
                if (matchProp && propName) {
                    let queryValue = substringMatch ? encodeURIComponent('%' + query + '%') : encodeURIComponent(query)
                    url += '&property=' + encodeURIComponent(propName) + '&property.value=' + queryValue
                    if (substringMatch) {
                        url += '&property.operation=like'
                    }
                } else {
                    url += '&nodename=' + encodeURIComponent(query)
                }
            }
            properties.forEach((prop, index) => {
                url += `&${index + 1}_property=${encodeURIComponent(prop)}`
            })

            $.getJSON(url, function(data) {
                $('#nodemorph-search-form button[type="submit"]').prop('disabled', false)
                const tbody = $('#nodemorph-search-results .coral-Table-body').empty();
                const thead = $('#search-results-header').empty()
                const resultCount = data.results;
                $('#result-text').text(`Found ${resultCount} result${resultCount === 1 ? '' : 's'}`)
                lastSearchResults = data.hits
                $('#export-csv-btn').css('display', resultCount > 0 ? 'inline-block' : 'none')
                $('#nodemorph-search-results').css('display', resultCount > 0 ? 'table' : 'none')

                if (data.hits && data.hits.length > 0) {
                    const displayHeaders = propertiesInput ? properties : ['Title', 'Type']
                    const headers = ['Path'].concat(properties.length > 0 ? properties : [PN_JCR_TITLE, PN_JCR_PRIMARY_TYPE])
                    const headerRow = document.createElement('tr')
                    headerRow.className = 'coral-Table-row'
                    headerRow.innerHTML = headers.map(h => `<th class="coral-Table-headerCell">${h}</th>`).join('')
                    thead.append(headerRow)

                    data.hits.forEach(function(hit) {
                        const row = document.createElement('tr');
                        row.className = 'coral-Table-row';
                        const cells = [hit[PN_JCR_PATH]]
                        if (propertiesInput) {
                            properties.forEach(prop => cells.push(hit[prop] || '-'))
                        } else {
                            cells.push(hit[PN_JCR_TITLE] || '-', hit[PN_JCR_PRIMARY_TYPE])
                        }
                        row.innerHTML = cells.map(cell => `<td class="coral-Table-cell">${cell}</td>`).join('')
                        if (verbose) {
                            const props = Object.entries(hit)
                                .filter(([key]) => ![PN_JCR_PATH, 'name', PN_JCR_PRIMARY_TYPE].includes(key))
                                .map(([key, val]) => `${key}: ${val}`)
                                .join('\n');
                            row.setAttribute('title', props);
                        }
                        tbody.append(row);
                    });
                } else {
                    tbody.innerHTML = '<tr class="coral-Table-row"><td class="coral-Table-cell" colspan="' + (properties.length + 1 || 3) + '">No results found</td></tr>'
                }
            }).fail(function(xhr) {
                $('#nodemorph-search-form button[type="submit"]').prop('disabled', false)
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

            const propertiesInput = $('#properties-field').val()
            const properties = propertiesInput ? propertiesInput.split(',').map(p => p.trim()).filter(p => p) : [PN_JCR_TITLE, PN_JCR_PRIMARY_TYPE]
            const displayHeaders = propertiesInput ? properties : ['Title', 'Type']
            const headers = ['Path'].concat(displayHeaders)
            const csvRows = [headers.map(h => `"${h}"`).join(',')]

            lastSearchResults.forEach(hit => {
                const row = [hit[PN_JCR_PATH]]
                if (properties.length > 0) {
                    properties.forEach(prop => row.push(hit[prop] || '-'))
                } else {
                    row.push(hit[PN_JCR_TITLE] || '-', hit[PN_JCR_PRIMARY_TYPE])
                }
                csvRows.push(row.map(val => `"${String(val).replace(/"/g, '""')}"`).join(','))
            });

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
                $('#copy-fields').addClass('visible')
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
            $('#add-prop-list').append(createPropertyRow('properties[]'))
            setPanelHeight('add-fields')
        })
        $(document).on('click', '#add-prop-list .remove-prop', function(e) {
            e.preventDefault()
            $(this).closest('.property-row').remove()
            setPanelHeight('add-fields')
        })

        $('#add-newnode-prop').on('click', function(e) {
            e.preventDefault()
            $('#newnode-prop-list').append(createPropertyRow('newNodeProperties[]'))
            setPanelHeight('create-fields')
        })
        $(document).on('click', '#newnode-prop-list .remove-prop', function(e) {
            e.preventDefault()
            $(this).closest('.property-row').remove()
            setPanelHeight('create-fields')
        })

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
                        formData.jcrNodeName = $('input[name="jcrNodeName"]').val()
                    }
                    formData.properties = $('input[name="properties[]"]').map(function() { return $(this).val(); }).get().join('\n')
                    break
                case 'delete':
                    formData.propNames = $('input[name="propNames"]').val();
                    break;
                case 'replace':
                    formData.propName = $('input[name="propName"]').val();
                    formData.find = $('input[name="find"]').val();
                    formData.partialMatch = $('coral-checkbox[name="partialMatch"]').prop('checked')
                    formData.replace = $('input[name="replace"]').val();
                    break;
                case 'copy':
                    formData.copyType = $('#copy-type').val();
                    formData.source = $('input[name="source"]').val();
                    formData.target = $('input[name="target"]').val();
                    break;
                case 'create':
                    formData.newNodeName = $('input[name="newNodeName"]').val()
                    formData.newNodeType = $('input[name="newNodeType"]').val()
                    formData.parentMatchCondition = $('input[name="parentMatchCondition"]').val()
                    formData.newNodeProperties = $('input[name="newNodeProperties[]"]').map(function() {return $(this).val()}).get().join('\n')
                    break
            }

            $.post('/bin/nodemorph/update', formData)
                .done(function(data) {
                    if (data.actions && data.actions.length > 0 && data.actions.some(action => action.status === "Failed")) {
                        $('#update-result-text').text('Update failed')
                        $('#nodemorph-update-results').css('display', 'none')
                        let errorMessage = 'An unexpected error occurred.'
                        const failedAction = data.actions.find(action => action.status === "Failed")
                        if (failedAction && failedAction.message) {
                            errorMessage = failedAction.message
                        }
                        const dialog = new Coral.Dialog().set({
                            id: 'update-error-dialog',
                            variant: 'error',
                            header: { innerHTML: 'Update Failed' },
                            content: { innerHTML: errorMessage },
                            footer: {
                                innerHTML: '<button is="coral-button" variant="primary" coral-close>OK</button>'
                            }
                        })
                        document.body.appendChild(dialog)
                        dialog.show()
                    } else {
                        $('#update-result-text').text(`Affected ${data.total} node${data.total === 1 ? '' : 's'}`)
                        const tbody = $('#nodemorph-update-results .coral-Table-body').empty()
                        $('#nodemorph-update-results').css('display', data.total > 0 ? 'table' : 'none')
                        data.actions.forEach(action => {
                            const row = `<tr class="coral-Table-row">
                                <td class="coral-Table-cell">${action.path}</td>
                                <td class="coral-Table-cell">${action.action}</td>
                                <td class="coral-Table-cell">${action.status}</td>
                            </tr>`
                            tbody.append(row)
                        });
                    }
                })
                .fail(function(xhr) {
                    $('#update-result-text').text('Update failed')
                    $('#nodemorph-update-results').css('display', 'none')
                    const dialog = new Coral.Dialog().set({
                        id: 'update-error-dialog',
                        variant: 'error',
                        header: { innerHTML: 'Update Failed' },
                        content: { innerHTML: xhr.responseText || 'Server error occurred. Check logs.' },
                        footer: {
                            innerHTML: '<button is="coral-button" variant="primary" coral-close>OK</button>'
                        }
                    })
                    document.body.appendChild(dialog)
                    dialog.show()
                })
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

    // Update the panel height when adding or removing property fields
    function setPanelHeight(panelId) {
        const $panel = $(`#${panelId}`)
        if ($panel.hasClass('visible')) {
            $panel.css('max-height', $panel[0].scrollHeight + 'px')
        }
    }

    // Create HTML for a property field
    function createPropertyRow(nameAttr) {
        return $('<div class="property-row">' +
            `<input is="coral-textfield" name="${nameAttr}" placeholder="key=value" />` +
            '<button is="coral-button" class="remove-prop" variant="quiet" icon="delete" iconsize="S"></button>' +
            '</div>')
    }

})(jQuery, Coral)
