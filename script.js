const torchBtn = document.getElementById('torchBtn');
const statusText = document.getElementById('status');
let isOn = false;
let track = null;

async function toggleTorch() {
    try {
        if (!track) {
            const stream = await navigator.mediaDevices.getUserMedia({
                video: { facingMode: 'environment' }
            });
            track = stream.getVideoTracks()[0];
        }

        isOn = !isOn;
        await track.applyConstraints({
            advanced: [{ torch: isOn }]
        });

        if (isOn) {
            torchBtn.classList.add('active');
            statusText.innerText = "ON";
            statusText.style.color = "#ffcc00";
        } else {
            torchBtn.classList.remove('active');
            statusText.innerText = "OFF";
            statusText.style.color = "#888";
        }
    } catch (err) {
        alert("টর্চলাইট ফ্ল্যাশ অ্যাক্সেস করা যাচ্ছে না অথবা আপনার ডিভাইসে এটি সাপোর্ট করছে না।");
        console.error(err);
    }
}

torchBtn.addEventListener('click', toggleTorch);
