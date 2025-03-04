/**
 * Moderation Logs Functions for FrizzlenMod Admin Panel
 */

/**
 * Loads the moderation logs
 * 
 * @param {number} page The page number
 * @param {string} filter The filter to apply (player, action, or timerange)
 * @param {Object} filterParams The filter parameters
 */
async function loadModLogs(page = 0, filter = null, filterParams = {}) {
    try {
        console.log("Loading mod logs with page:", page, "filter:", filter);
        
        // Show loading indicator
        showSpinner('modlogs-container');
        
        // Clear any previous error messages
        const errorElement = document.getElementById('modlogs-error');
        if (errorElement) {
            errorElement.innerHTML = '';
            errorElement.style.display = 'none';
        }
        
        // Build the API URL based on filter
        let url = modlogs;
        if (filter === 'player') {
            url = API.modlogs.player(filterParams.playerName, page, PAGE_SIZE);
        } else if (filter === 'action') {
            url = API.modlogs.action(filterParams.action, page, PAGE_SIZE);
        } else if (filter === 'timerange') {
            url = API.modlogs.timeRange(filterParams.start, filterParams.end, page, PAGE_SIZE);
        } else {
            url = API.modlogs.all(page, PAGE_SIZE);
        }
        
        console.log("Fetching from URL:", url);
        
       
		
		
		
		 // Fetch moderation logs
        const response = await apiGet(url);
        console.log("Mod logs API response:", response);
        
        // Check for various response formats and adapt accordingly
        if (response && (response.success || response.logs)) {
            // Display moderation logs - some APIs might return logs directly, others nested
            const logs = response.logs || response;
            // Pass pagination if available
            const paginationInfo = response.pagination || {
                page: page,
                totalPages: response.totalPages || 1
            };
            
            displayModLogs(logs, paginationInfo);
            
            // Update filter UI
            updateModLogsFilterUI(filter, filterParams);
        } else {
            console.error("Failed to load mod logs:", response?.error || "Unknown error");
            showErrorMessage('Failed to load moderation logs: ' + (response?.error || 'Unknown error'), 'modlogs-error');
        }
        
    } catch (error) {
        console.error('Error loading moderation logs:', error);
        showErrorMessage('Failed to load moderation logs: ' + error.message, 'modlogs-error');
    }
}

/**
 * Shows an error message in the specified container
 * 
 * @param {string} message The error message to show
 * @param {string} containerId The ID of the container element
 */
function showErrorMessage(message, containerId) {
    const container = document.getElementById(containerId);
    if (container) {
        container.innerHTML = `<div class="alert alert-danger">${message}</div>`;
        container.style.display = 'block';
    } else {
        console.error(`Error container ${containerId} not found`);
    }
}

/**
 * Displays moderation logs
 * 
 * @param {Array} logs The moderation logs
 * @param {Object} response The full API response
 */
function displayModLogs(logs, response) {
    const modlogsContainer = document.getElementById('modlogs-container');
    
    if (!modlogsContainer) return;
    
    if (!logs || logs.length === 0) {
        modlogsContainer.innerHTML = '<div class="alert alert-info">No moderation logs found.</div>';
        return;
    }
    
    let html = `
        <div class="table-responsive">
            <table class="table table-striped">
                <thead>
                    <tr>
                        <th>Date</th>
                        <th>Moderator</th>
                        <th>Action</th>
                        <th>Target</th>
                        <th>Reason</th>
                        <th>Duration</th>
                    </tr>
                </thead>
                <tbody>
    `;
    
    logs.forEach(log => {
        const actionClass = log.action.includes('BAN') ? 'danger' : 
                          log.action.includes('MUTE') ? 'warning' : 
                          log.action.includes('WARN') ? 'info' : 'secondary';
        
        html += `
            <tr>
                <td>${formatDate(log.timestamp)}</td>
                <td>${log.moderator}</td>
                <td><span class="badge bg-${actionClass}">${log.action}</span></td>
                <td>${log.target}</td>
                <td>${truncateString(log.reason, 50)}</td>
                <td>${log.duration || 'N/A'}</td>
            </tr>
        `;
    });
    
    html += `
                </tbody>
            </table>
        </div>
    `;
    
    // Add pagination
    if (response.totalPages > 1) {
        html += `
            <div class="pagination-container">
                ${createPagination(response, (page) => loadModLogs(page))}
            </div>
        `;
    }
    
    modlogsContainer.innerHTML = html;
}

/**
 * Updates the moderation logs filter UI
 * 
 * @param {string} activeFilter The active filter
 * @param {Object} filterParams The filter parameters
 */
function updateModLogsFilterUI(activeFilter, filterParams) {
    // Update active filter buttons
    const filterButtons = document.querySelectorAll('.modlogs-filter-btn');
    filterButtons.forEach(btn => {
        btn.classList.remove('active');
        if (btn.dataset.filter === activeFilter) {
            btn.classList.add('active');
        }
    });
    
    // Update filter form visibility
    document.getElementById('player-filter-form').style.display = (activeFilter === 'player') ? 'block' : 'none';
    document.getElementById('action-filter-form').style.display = (activeFilter === 'action') ? 'block' : 'none';
    document.getElementById('timerange-filter-form').style.display = (activeFilter === 'timerange') ? 'block' : 'none';
    
    // Update form values if filter is active
    if (activeFilter === 'player' && filterParams.playerName) {
        document.getElementById('player-filter-input').value = filterParams.playerName;
    } else if (activeFilter === 'action' && filterParams.action) {
        document.getElementById('action-filter-select').value = filterParams.action;
    } else if (activeFilter === 'timerange') {
        if (filterParams.start) document.getElementById('start-date-input').value = new Date(filterParams.start).toISOString().split('T')[0];
        if (filterParams.end) document.getElementById('end-date-input').value = new Date(filterParams.end).toISOString().split('T')[0];
    }
}

/**
 * Handles the player filter form submission
 * 
 * @param {Event} event The form submit event
 */
function handlePlayerFilter(event) {
    event.preventDefault();
    
    const playerName = document.getElementById('player-filter-input').value;
    
    if (playerName) {
        loadModLogs(0, 'player', { playerName });
    }
}

/**
 * Handles the action filter form submission
 * 
 * @param {Event} event The form submit event
 */
function handleActionFilter(event) {
    event.preventDefault();
    
    const action = document.getElementById('action-filter-select').value;
    
    if (action) {
        loadModLogs(0, 'action', { action });
    }
}

/**
 * Handles the time range filter form submission
 * 
 * @param {Event} event The form submit event
 */
function handleTimeRangeFilter(event) {
    event.preventDefault();
    
    const startDate = document.getElementById('start-date-input').value;
    const endDate = document.getElementById('end-date-input').value;
    
    if (startDate && endDate) {
        const start = new Date(startDate).getTime();
        const end = new Date(endDate).getTime() + (24 * 60 * 60 * 1000); // Add a day to include the end date
        
        loadModLogs(0, 'timerange', { start, end });
    }
}

/**
 * Clears all filters and loads all moderation logs
 */
function clearModLogsFilters() {
    // Reset form values
    document.getElementById('player-filter-input').value = '';
    document.getElementById('action-filter-select').value = '';
    document.getElementById('start-date-input').value = '';
    document.getElementById('end-date-input').value = '';
    
    // Load all logs
    loadModLogs();
} 