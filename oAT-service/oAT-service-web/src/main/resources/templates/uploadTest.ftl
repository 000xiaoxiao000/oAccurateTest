<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>Title</title>
    <script src="/js/jquery.min.js"></script>
    <script src="/js/spark-md5.min.js"></script>
      <#include "common.ftl">
</head>
<body>
<script type="text/javascript">
    function uploadFile() {
        // 计算md5 值
        doMd5(document.getElementById("file"), function (md5) {
            var fileObj = document.getElementById("file").files[0]; // js 获取文件对象
            var FileController = "http://localhost:8080/resource/upload";                    // 接收上传文件的后台地址
            // FormData 对象
            var form = new FormData($("#uploadForm")[0]);
            form.append("md5", md5)
            // XMLHttpRequest 对象
            var xhr = new XMLHttpRequest();
            xhr.open("post", FileController, true);
            xhr.onload = function () {
                showToast("上传完成!", 'success');
            };
            xhr.upload.addEventListener("progress", progressFunction, false);
            xhr.send(form);
        });
    }

    function progressFunction(evt) {
        var progressBar = document.getElementById("progressBar");
        var percentageDiv = document.getElementById("percentage");
        if (evt.lengthComputable) {
            progressBar.max = evt.total;
            progressBar.value = evt.loaded;
            percentageDiv.innerHTML = Math.round(evt.loaded / evt.total * 100) + "%";
            /*if (evt.loaded == evt.total) {
                alert("上传完成100%");
            }*/
        }
    }


    function doMd5(fileItem, call) {
        var blobSlice = File.prototype.slice || File.prototype.mozSlice || File.prototype.webkitSlice;
        var file = fileItem.files[0];
        var chunkSize = 2097152; // read in chunks of 2MB
        var chunks = Math.ceil(file.size / chunkSize);
        var currentChunk = 0;
        var spark = new SparkMD5.ArrayBuffer();
        var fileReader = new FileReader();
        var begin = new Date().getTime();
        var md5Result;
        fileReader.onload = function (e) {
            //  log.innerHTML+="\nread chunk number "+parseInt(currentChunk+1)+" of "+chunks;
            spark.append(e.target.results); // append array buffer
            currentChunk++;
            if (currentChunk < chunks) {
                loadData();
            } else {
                var md5Val = spark.end();
                console.log("md5 值:" + md5Val + " 用时:" + (new Date().getTime() - begin));
                call(md5Val);
            }
        };
        fileReader.onerror = function () {
            showToast("md5计算异常", 'error');
        };

        function loadData() {
            var start = currentChunk * chunkSize,
                    end = ((start + chunkSize) >= file.size) ? file.size : start + chunkSize;
            fileReader.readAsArrayBuffer(blobSlice.call(file, start, end));
        };
        loadData();
        return md5Result;
    }

</script>

<br/>
<br/>
<br/>
<br/>

<progress id="progressBar" value="0" max="100"></progress>
<span id="percentage"></span>

<br/>
<br/>
<br/>
<br/>


<form id="uploadForm">
    <input type="file" id="file" name="file"/>
    <input type="button" onclick="uploadFile();" value="上传"/>
</form>
</body>
</html>
