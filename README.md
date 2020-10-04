# zoopla-parser
Do some stuff with https://www.zoopla.co.uk/ API

TODO: This file should be more user-friendly. 

# How to run it?
You need to create a config.txt which looks like this:
```
{
    "telegramChatIds" : [123, 456],
    "telegramAPIKey" : "123123123:ABCABCABCABCABCABCAB-ABCAbc1AbCab1c",
    "dbURL" : "jdbc:postgresql:youruser",
    "dbUser" : "youruser",
    "dbPassword" : "password",
    "tesseractPathData" : "/pass/to/tessdata"
}
```
And also you need to create postresql database with some tables (TODO: write which one do you really need).
```
CREATE TABLE public.seen_properties (
    id integer NOT NULL
);
CREATE TABLE public.seen_properties_right_move (
    id integer NOT NULL
);
```

Also you need to download eng tessdata (probably this one -- https://github.com/tesseract-ocr/tessdata/blob/master/eng.traineddata) 
And maybe install some java/gradle stuff.

Then you can run ./start.sh (or maybe put it in crontab to run every hour or so).

# What it can do?
* Parses zoopla for a properties uploaded during last 24 hours. 
* It has a list of built-in search queries (in Main.kt) to specify which areas of London to search.
* Leaves only properties inside some money range (< 3500£ per month)
* Leaves only properties with a floor plan
* Does some OCR magic to find propery area in the floor plan (zoopla doesn't provide this number in any better way). Sometimes it fails, but it works okay in 95% of cases. It also can converts things between square meters and square feet. 
* Leaves only properties with big area (> 55 sq. m)
* Sends a telegram messages to all users/chats from telegramChatIds (from config.txt)
* Telegram messages contain photos, zoopla link, link to google maps, price and area.
* Uses postrges database to not send messages about same properties
* Caches results of quering zoopla on a local disk.

![](https://sun6-16.userapi.com/ibinSm-INp_xUuhiNGiQW_P34s86_oPf3Kn07A/tSRdn5lKBeo.jpg)

