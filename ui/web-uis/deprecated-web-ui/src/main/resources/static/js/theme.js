// Theme switching functionality for ULTRACARDS

document.addEventListener('DOMContentLoaded', function() {
    // Get the theme toggle checkbox
    const themeToggle = document.getElementById('theme-toggle');
    
    // Check if user has previously selected a theme
    const currentTheme = localStorage.getItem('theme') || 'dark';
    
    // Apply the saved theme or default to dark
    document.body.setAttribute('data-theme', currentTheme);
    
    // Update the toggle switch position based on the current theme
    if (themeToggle) {
        themeToggle.checked = currentTheme === 'light';
        
        // Add event listener to the toggle switch
        themeToggle.addEventListener('change', function() {
            if (this.checked) {
                // Switch to light mode
                document.body.setAttribute('data-theme', 'light');
                localStorage.setItem('theme', 'light');
            } else {
                // Switch to dark mode
                document.body.setAttribute('data-theme', 'dark');
                localStorage.setItem('theme', 'dark');
            }
        });
    }
});