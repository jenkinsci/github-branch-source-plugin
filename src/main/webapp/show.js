function updateUI(list) {
    for( var i = 0; i < list.length; i++){
        if( list[i].checked){
            list[i].click()
        }
    }
}

/*
 When loading this plugin because of the Ajax call data is loaded after the page loads that means when the page is rendered
 is not showing data adn the fields are hidden (by CSS). This call emulates a click in the label after the load and shows the
 data.
 */
setTimeout(updateUI, 200, document.getElementsByClassName("radio-block-control"));
