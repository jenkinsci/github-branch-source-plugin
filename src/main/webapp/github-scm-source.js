// We match the end of the name using $= because the beginning of the name is 
// dynamically generated to avoid name clashes.
Behaviour.specify("input[name$=_isConfiguredByUrl]", 'GitHubSCMSourceRadioConfiguration', 0, function(e) {
    if (e.gitHubSCMSourceRadioConfiguration) {
        return;
    }
    // Todo: Replace with a query selector?
    var findNeighboringDynamicInput = function(e) {
        var getNthParent = function(e, n) {
            while (n > 0) {
                if (e.parentNode) {
                    e = e.parentNode;
                    n--;
                } else {
                    return null;
                }
            }
            return e;
        }
        var inputTbody = getNthParent(e, 4 /*tbody > tr > td > label > input*/);
        if (inputTbody) {
            var dynInputs = document.getElementsByName("isConfiguredByUrlDynamicValue");
            for (var i = 0; i < dynInputs.length; i++) {
                var dynInputTbody = getNthParent(dynInputs[i], 3 /*tbody > tr > td > input*/);
                if (dynInputTbody && inputTbody.isSameNode(dynInputTbody)) {
                    return dynInputs[i];
                }
            }
        }
    }
    var neighboringDynamicInput = findNeighboringDynamicInput(e);
    if (neighboringDynamicInput) {
        e.onclick = function() {
            neighboringDynamicInput.value = e.value;
        };
    }
    e.gitHubSCMSourceRadioConfiguration = true;
});
