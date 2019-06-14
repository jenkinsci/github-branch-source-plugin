function updateUI(list) {
    for( var i = 0; i < list.length; i++){
        if( list[i].checked){
            list[i].click()
        }
        defaultValue(list[i].name)
    }
}

function defaultValue(name){
    var list = document.getElementsByName(name)
    var checked = false;
    for( var i = 0; i < list.length; i++){
        if( list[i].checked){
            checked = true;
        }
    }
    if (!checked){
        list[list.length - 1].click();
    }

}

setTimeout(updateUI, 200, document.getElementsByClassName("radio-block-control"));
