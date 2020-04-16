var cardCount = 0;

if (!!window.EventSource) {
    var eventSource = new EventSource("/object/stream");
    eventSource.onmessage = function(event) {
        data = JSON.parse(event.data);
        showResult(data);
    };

    $.get('/object/labels',
        function(data, textStatus, jqXHR) { // success callback
            var labelList = $('#labels-list');

            for (var i in data) {
                var label = data[i];
                labelList.append('<li><a href="https://unsplash.com/s/photos/' + label + '" target="_new" style="color:gray">' + label + '</a></li>');
            }
        });
} else {
    window.alert("EventSource not available on this browser.")
}

function highlightBB(cardCount, bbCount) {
    $('#bb-' + cardCount + '-' + bbCount).css('border', '2px solid red');
}

function resetBB(cardCount, bbCount) {
    $('#bb-' + cardCount + '-' + bbCount).css('border', '2px solid #007bff');
}

function doSubmit() {
    var formData = new FormData();

    var fileSelect = document.getElementById("fileSelect");
    if (fileSelect.files && fileSelect.files.length == 1) {
        var file = fileSelect.files[0]

        // Demo safeguard
        if(file.name.indexOf("unsplash") == -1) {
            alert("Warning: Please upload a picture only from the provided links.");
            return;
        }

        formData.set("file", file, file.name);
    }

    // Http Request
    var request = new XMLHttpRequest();
    request.onreadystatechange = function() {
        if (this.readyState == 4 && this.status == 200) {
            data = JSON.parse(request.responseText);

            if (data.error != null) {
                var alert = '<div id="alert" class="alert alert-danger alert-fixed alert-dismissible fade show" role="alert"><span>There was an error reading your image. <b>Please try a different one.</b></span><button type="button" class="close" data-dismiss="alert" aria-label="Close"><span aria-hidden="true">&times;</span></button></div>';
                $("body").append(alert);
            }
        }
    };

    request.open('POST', "/object/detect/" + 65);
    request.setRequestHeader("Accept", "application/json,text/plain");
    request.send(formData);
}

function showResult(response) {
    var uuid = response.uuid;

    $.get('/object/data?uuid=' + uuid,
        function(data, textStatus, jqXHR) { // success callback
            if (data.error != null) {
                $("#alert").addClass("show");
            } else {
                displayResult(response, data);
            }
        });
}

function displayResult(response, data) {
    var html = '';
    var results = response.results;
    var mediaType = response.mediaType;

    var imageWidth = response.width;
    var imageHeight = response.height;
    var error = response.error;

    if (error != null) return;

    // Demo Safeguard: Do not show images without any detected objects
    if(results.length == 0) return;

    $('#cards').prepend('<div class="card w-300"><div id="card-' + cardCount + '" style="position: relative"></div><div class="card-body" id="card-body-' + cardCount + '"></div></div>');
    $('#card-' + cardCount).append('<img class="card-img-top" width="' + imageWidth + '" src="data:' + mediaType + ';base64,' + data + '">');

    for (var i = 0; i < results.length; i++) {

        var label = results[i].label;
        var score = results[i].score;
        var badgeType = 'primary';
        if (score < 0) {
            badgeType = 'danger';
            score = label;
            label = 'error';
        } else {
            score = parseFloat(results[i].score * 100).toFixed(2);
        }

        html += '<span class="badge badge-pill badge-' + badgeType + '" data-toggle="tooltip" data-placement="right" title="' + score + '% Confident" onmouseover="highlightBB(' + cardCount + ',' + i + ')" onmouseout="resetBB(' + cardCount + ',' + i + ')">' + label + '</span> ';

        var x1 = parseFloat(results[i].x1 * 100).toFixed(2);
        var y1 = parseFloat(results[i].y1 * 100).toFixed(2);
        var x2 = parseFloat(results[i].x2 * 100).toFixed(2);
        var y2 = parseFloat(results[i].y2 * 100).toFixed(2);

        boundingWidth = x2 - x1;
        boundingHeight = y2 - y1;

        $('#card-' + cardCount).append('<div id="bb-' + cardCount + '-' + i + '" style="position: absolute; top: ' + y1 + '%; left: ' + x1 + '%; width: ' + boundingWidth + '%; height: ' + boundingHeight + '%; border: 2px solid #007bff"></div>');
    }

    $('#card-body-' + cardCount).html(html);
    cardCount++;
}

function readURL(input) {
    if (input.files && input.files[0]) {
        var reader = new FileReader();
        reader.readAsDataURL(input.files[0]);
        doSubmit();
    }
}

$("#fileSelect").on('change', function() {
    readURL(this);
});