
function Calc()
{
	this.validate = validate;
	this.clear = clear;
	this.clearAll = clearAll;
	this.evaluate = evaluate;
	this.append = append;
	this.setTxtCalcFocus = setTxtCalcFocus;
	this.isNumberKey = isNumberKey;

	function validate(displayTextId, calcTextId, integral, separatorKey, e)
	{
	     var key;

	     if(window.event)
	          key = e.keyCode; //IE
	     else
	          key = e.which;   //Firefox

	     if(key == 13 || key == 61) // Enter, =
	     {
	     	evaluate(displayTextId, calcTextId);
	        return false;
	     }
	     else if (key == 0) // control, delete, ...
	     {
	     	return true;
	     }
	     else if (key == 8) // backspace
	     {
	     	return true;
	     }
	     else if (key >= 17 && key <= 20) // Control
	     {
	     	return true;
	     }
	     else if (key == 32) // space
	     {
	     	return true;
	     }
	     else if (key >= 48 && key <= 57) // 0 - 9
	     {
	     	return true;
	     }
	     else if (key == 42 || key == 43 || key == 45 || key == 47 || key == 37) // *, +, -, /, %
	     {
	     	return true;
	     }
	     else if ( key == separatorKey && !integral)
	     {
	     	return true;
	     }
	     else
	     {
	     	return false;
	     }
	}

	function clearAll(calcTextId)
	{
		try
		{
			var calcText = document.getElementById(calcTextId);
			calcText.value = "";
		}
		catch (err)
		{
		}
		finally
		{
			document.getElementById(calcTextId).focus();
		}
	}

	function clear(calcTextId)
	{
		try
		{
			var calcText = document.getElementById(calcTextId);
			var val = calcText.value;
			if (val != "")
			{
				val = val.substring(0, val.length - 1);
				calcText.value = val;
			}
		}
		catch (err)
		{
		}
		finally
		{
			document.getElementById(calcTextId).focus();
		}
	}

	function evaluate(displayTextId, calcTextId, separator)
	{
		try
		{
			var calcText = document.getElementById(calcTextId);
			var value = calcText.value;
			if (separator != '.')
			{
				var re = new RegExp("[" + separator + "]");
				value = value.replace(re,'.');
			}
			var result = "" + eval(value);
			if (separator != '.')
			{
				result = result.replace(/\./, separator);
			}
			calcText.value = result;

			var displayText = document.getElementById(displayTextId);

			if (!displayText.readOnly && calcText.value != 'undefined')
			{
				displayText.value = calcText.value;
				setTimeout("document.getElementById('" + displayTextId + "').focus()", 100);
			}
		}
	   	catch (err)
	   	{
	   	}
	   	finally
		{
			document.getElementById(calcTextId).focus();
		}
	   	
	}

	function append(calcTextId, val)
	{
		var calcText = document.getElementById(calcTextId);
		calcText.value += val;
		setTimeout("document.getElementById('" + calcTextId + "').focus()", 50);
	}
	
	function setTxtCalcFocus(calcTextId)
	{
		document.getElementById(calcTextId).focus();
	}
	
	function isNumberKey(evt) {
		var charCode = (evt.which) ? evt.which : evt.keyCode;
        if ((charCode != 46 || $(evt.target).val().indexOf('.') != -1) && charCode > 31 && (charCode < 48 || charCode > 57))
            return false;
		return true;
	}
}

var calc = new Calc();