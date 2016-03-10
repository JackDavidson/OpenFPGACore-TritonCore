

$(document).ready(function() {
  $('body').css('display', 'none');
  $('body').fadeIn(1000);
  $('#more-btn').click(function() {
    if(window.location.href.indexOf("index.html") > -1)
      newLocation = 'pgs/overview.html';
    else if(window.location.href.indexOf("overview.html") > -1)
      newLocation = 'fpga.html';
    else if(window.location.href.indexOf("fpga.html") > -1)
      newLocation = 'fpgadetail.html';
    else if(window.location.href.indexOf("fpgadetail.html") > -1)
      newLocation = 'lut.html';
    else if(window.location.href.indexOf("lut.html") > -1)
      newLocation = 'grt.html';
    else if(window.location.href.indexOf("grt.html") > -1)
      newLocation = 'tutorial.html';
    else if(window.location.href.indexOf("tutorial.html") > -1)
      newLocation = 'sbtsetup.html';
    else if(window.location.href.indexOf("sbtsetup.html") > -1)
      newLocation = 'gitsetup.html';
    else if(window.location.href.indexOf("gitsetup.html") > -1)
      newLocation = 'netlistsetup.html';
    else if(window.location.href.indexOf("netlistsetup.html") > -1)
      newLocation = 'bitstreamsetup.html';
    else if(window.location.href.indexOf("bitstreamsetup.html") > -1)
      newLocation = 'compileverilog.html';
    $('body').fadeOut(1000, newpage);
  });
});

function newpage() {
  window.location = newLocation;
}
