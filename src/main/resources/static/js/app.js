// Progressive enhancement only: the UI works without JavaScript.
document.addEventListener('DOMContentLoaded', function () {
  document.querySelectorAll('form[data-busy]').forEach(function (form) {
    form.addEventListener('submit', function (ev) {
      var btn = ev.submitter || form.querySelector('button[type=submit]');
      if (btn && btn.dataset.busyLabel) {
        setTimeout(function () { btn.disabled = true; btn.textContent = btn.dataset.busyLabel; }, 0);
        var live = document.getElementById('busy-status');
        if (live) { live.textContent = btn.dataset.busyLabel; }
      }
    });
  });
  var hash = window.location.hash;
  if (hash && hash.indexOf('#cite-') === 0) {
    var el = document.getElementById(hash.substring(1));
    if (el) { el.classList.add('highlight'); el.focus(); }
  }
});
