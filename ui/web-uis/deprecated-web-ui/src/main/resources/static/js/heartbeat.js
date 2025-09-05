setInterval(() => {
    fetch('/api/heartbeat', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ timestamp: Date.now() })
    })
        .then(res => {
            if (!res.ok) {
                console.error("Heartbeat failed with status:", res.status);
            }
        })
        .catch(err => console.error("Heartbeat request failed:", err));
}, 10000);
