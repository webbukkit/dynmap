<%@ Page Language="C#" %>
<script runat="server" language="C#">
public void Page_Load(object sender, EventArgs e)
{
    string path = Request.Params["path"];
    System.Net.HttpWebRequest request = (System.Net.HttpWebRequest)System.Net.WebRequest.Create("http://localhost:8123/up/" + path);

    if (Request.HttpMethod.Equals("POST"))
    {
        request.ContentType = Request.ContentType;
        request.ContentLength = Request.ContentLength;
        request.Method = Request.HttpMethod;

        //Read the request's data and write it to the WebClient's request.
        System.IO.Stream requestStream = request.GetRequestStream();

        byte[] requestData = new byte[Request.ContentLength];
        Request.InputStream.Read(requestData, 0, requestData.Length);
        requestStream.Write(requestData, 0, requestData.Length);
        requestStream.Close();
    }

    //Get the response from the server.
    System.Net.WebResponse response = request.GetResponse();
    System.IO.Stream responseStream = response.GetResponseStream();
    System.IO.StreamReader reader = new System.IO.StreamReader(responseStream);
    Response.ContentType = response.ContentType;
    Response.Write(reader.ReadToEnd());
    reader.Close();
    responseStream.Close();
    response.Close();
}
</script>