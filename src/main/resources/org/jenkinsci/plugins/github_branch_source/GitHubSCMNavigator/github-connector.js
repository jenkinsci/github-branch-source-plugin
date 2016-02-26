// awful hack to get around JSONifying things with Prototype taking over wrong. ugh. Prototype is the worst.
(function() {
    var stringify = function(o) {
        if(Array.prototype.toJSON) { // Prototype f's this up something bad
            var protoJSON = {
                a: Array.prototype.toJSON,
                o: Object.prototype.toJSON,
                h: Hash.prototype.toJSON,
                s: String.prototype.toJSON
            };
            try {
                delete Array.prototype.toJSON;
                delete Object.prototype.toJSON;
                delete Hash.prototype.toJSON;
                delete String.prototype.toJSON;
                
                return JSON.stringify(o);
            }
            finally {
                if(protoJSON.a) {
                    Array.prototype.toJSON = protoJSON.a;
                }
                if(protoJSON.o) {
                    Object.prototype.toJSON = protoJSON.o;
                }
                if(protoJSON.h) {
                    Hash.prototype.toJSON = protoJSON.h;
                }
                if(protoJSON.s) {
                    String.prototype.toJSON = protoJSON.s;
                }
            }
        }
        else {
            return JSON.stringify(o);
        }
    };
    
    var ajax = function(options) {
        var contentType = options.contentType;
        var body = options.data;
        
        if(!contentType) {
            if(Object.isString(body)) {
                contentType = 'application/x-www-form-urlencoded';
               }
               else {
                   contentType = 'application/json';
               }
           }
           
           if(contentType == 'application/json' && body) {
               body = stringify(body);
        }
        
        console.log(options.method + ' ' + options.url + ', hookBody: ' + body);

        new Ajax.Request(options.url, {
            method : options.method ? options.method : 'GET', // default to GET
            postBody: body,
            contentType: contentType,
            onSuccess:  function(response) {
                if(options.success) {
                    options.success(response.responseJSON);
                }
            },
            onFailure: function(response) {
                if(options.error) {
                    options.error(response.responseJSON);
                }
            },
            requestHeaders: options.requestHeaders
        });
    };
    
    var q = function(cssSelector) {
        var sel = $$(cssSelector);
        sel.on = function(event, callback) {
            document.observe(event, function(e, el) { // don't use document, prototype doesn't prevent bubbling properly
                 if (el = e.findElement(cssSelector)) {
                    if (e.stopPropagation) {
                        e.stopPropagation();
                    } else {
                        e.cancelBubble = true;
                    }
                    if(e.stopImmediatePropagation) {
                        e.stopImmediatePropagation();
                    }
                    if (e.preventDefault) {
                        e.preventDefault();
                    }
                    e.stop();
                    callback(e);
                }
            });
        };
        return sel;
    };
    
    //http://localhost:8080/jenkins/descriptor/com.cloudbees.plugins.credentials.CredentialsSelectHelper/addCredentials
    q('.create-github-api-key').on('click', function(e) {
        var apiKeyButton = e.element();
        var apiKeyForm = apiKeyButton.up('.create-github-api-key-form');
        var user = apiKeyForm.select('input[name=github-username]')[0].value;
        var pass = apiKeyForm.select('input[name=github-password]')[0] ? apiKeyForm.select('input[name=github-password]')[0].value : '';
        var idx = 0;//e.element().up('.repeated-chunk').previousSiblings().size();
        ajax({
            url: Element.readAttribute(apiKeyForm, 'data-url') + '/navigators/' + idx + '/createApiKey',
               method: 'POST',
               data: 'username='+encode(user)+'&password='+encode(pass),
            success: function(response) {
                console.log(response.id);
                window.credentials.refreshAll(); // refresh credentials, then select
                var parent = apiKeyForm.up('.setting-main');
                var selector = parent.select('.credentials-select');
                setTimeout(function() { // TODO call after refresh completes
                    apiKeyForm.removeClassName('active');
                    selector[0].select('option[value='+response.id+']')[0].selected = true;
                }, 500)
            },
            error: function(response) {
                var errorMessageDiv = $$('.error-message')[0];
                errorMessageDiv.style.display = 'inline-block';
                errorMessageDiv.innerHTML = response.error;
                console.log(response.error);
                //alert(response.error);
            }
        });
    });

    q('.create-github-api-key-show').on('click', function(e) {
         var el = e.element().nextSiblings()[0];
         if(el.hasClassName('active')) {
             el.removeClassName('active');
         }
         else {
             el.addClassName('active');
          }
     });
    
    q('.create-github-credential-show').on('click', function(e) {
         var el = e.element().nextSiblings()[0];
         if(el.hasClassName('active')) {
             el.removeClassName('active');
         }
         else {
             el.addClassName('active');
          }
     });
})();
