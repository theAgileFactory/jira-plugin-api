/*
Function to perform a post and get a response.
url : the URL API
data : the input JSON data (to be stringified)
ifDoneFunction : function to be called if the call is successful (data is passed as a parameter)
ifFailFunction : function to be called if the call fails
 */
function sita_plugin_performPost(url, data, ifDoneFunction, ifFailFunction){
	AJS.$.ajax({
		type: "POST",
		dataType: 'json',
		contentType: 'application/json; charset=utf-8',
		url: url,
		data: data
	}).done(ifDoneFunction).fail(ifFailFunction);
}

/*
Creates an HTML list from a dictionary
*/
function sita_plugin_getListAsHtml(dictionary,actionName,maxNumberOfRecords){
	var html="<ul>";
	for(key in dictionary){
		var keyVal = dictionary[key].split("~");
		var valToFunction = key;
		if(keyVal.length > 1) {
			valToFunction = valToFunction + "~" + keyVal[1]
		}
		html=html+"<li><a href=\"#\" onClick=\"return "+actionName+"('"+valToFunction+"');\">";
		html=html+keyVal[0];
		html=html+"</a></li>";
	}
	html=html+"</ul><small>number of displayed records is limited to "+maxNumberOfRecords+"</small>";
	return html;
}