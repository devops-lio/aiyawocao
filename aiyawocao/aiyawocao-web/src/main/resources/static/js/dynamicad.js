(function () {
  var ids = ["ld", "lsh"];
  for (var idx = 0; idx < ids.length; idx++) {
    if (document.getElementById(ids[idx])) {
      var seed = new Date().getMinutes();
      $.ajax({
        type: "GET",
        url: "/ad/rest/get?id=" + ids[idx] + "&seed=" + seed,
        dataType: "json",
        success: function (ret) {
          id = ret["id"];
          if (id) {
            var title = document.getElementById(id + "-title");
            if (title) {
              title.innerHTML = ret["title"];
            } else {
              console.log(id + " title not exist");
            }

            var content = document.getElementById(id + "-content");
            if (content) {
              content.innerHTML = ret["content"];
            } else {
              console.log(id + " content not exist");
            }

            enable = document.getElementById(id + "-enable");
            if (enable) {
              enable.value = ret["enable"];
            }

            if (ret["enable"] == "true") {
              var element = document.getElementById(id);
              if (element) {
                element.style.display = "";
              }
            }
          }
        },
        error: function (message) {
        }
      });
    }
  }
})();