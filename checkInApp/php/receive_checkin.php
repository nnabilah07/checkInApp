<?php
error_reporting(E_ALL);
ini_set('display_errors', 1);
$host = "localhost";
$dbname = "checkin_db";
$username = "checkin_user";
$password = "Dania"; // replace with your actual password

$conn = new mysqli($host, $username, $password, $dbname);

if ($conn->connect_error) {
    die("Connection failed: " . $conn->connect_error);
}

$user = $_POST['username'] ?? '';
$lat = $_POST['latitude'] ?? '';
$lon = $_POST['longitude'] ?? '';

if ($user && $lat && $lon) {
    $stmt = $conn->prepare("INSERT INTO checkins (username, latitude, longitude) VALUES (?, ?, ?)");
    $stmt->bind_param("sdd", $user, $lat, $lon);
    $stmt->execute();
    echo "Check-in successful!";
    $stmt->close();
} else {
    echo "Invalid input.";
}

$conn->close();
?>


