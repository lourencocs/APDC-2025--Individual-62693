function displayUserInfo() {
    const userNameDisplay = document.getElementById('userNameDisplay');
    const userRoleDisplay = document.getElementById('userRoleDisplay');
    const username = sessionStorage.getItem('username');
    const role = sessionStorage.getItem('role');

    if (username && role) {
        userNameDisplay.textContent = username;
        userRoleDisplay.textContent = role;
    } else {
        userNameDisplay.textContent = "None";
        userRoleDisplay.textContent = "None";
    }
}

document.addEventListener('DOMContentLoaded', displayUserInfo);