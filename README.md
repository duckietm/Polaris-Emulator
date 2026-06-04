# Arcturus Morningstar Extended #

Arcturus Morningstar Extended is as a fork of Arcturus Emulator by TheGeneral and modified by Krews. Arcturus Morningstar Extended is also released under the [GNU General Public License v3](https://www.gnu.org/licenses/gpl-3.0.txt) 
and is developed for free by talented developers and is compatible with the following client revision/community projects:

| Community Clients | [Nitro Client](https://github.com/billsonnn/nitro-react) |
| ------------- | ------------- |

## Download ##
[Latest compiled version](https://github.com/duckietm/Arcturus-Morningstar-Extended/tree/main/Latest_Compiled_Version)

## Connection ##
Use the BUILD-IN Websocket so do NOT load any websocket plugin!

### How do I connect to my emulator using Secure Websockets (wss)?
You have several options to add WSS support to your websocket server. 

- You can add your certificate and key file to the path `/ssl/cert.pem` and `/ssl/privkey.pem` to add WSS support directly to the server **Note**:The client will not accept self-signed certificates, you must use a certificate signed by a CA (you can get one for free from letsencrypt.org)
  
- **RECOMMENDED** You can proxy WSS with either cloudflare or nginx. **Note**: Adding a proxy means that you will have to configure `ws.nitro.ip.header` so that the plugin is able to get the player's real ip address, and not the IP address of the proxy.

### Proxying WSS with Cloudflare
You can easily proxy wss traffic using Cloudflare. However, you should first make sure that your `ws.nitro.port` is set to one that is listed as HTTPS Cloudflare Compatible in the following link:
https://support.cloudflare.com/hc/en-us/articles/200169156-Which-ports-will-Cloudflare-work-with-

As of writing this, the following ports are listed as compatible:
- 443
- 2053
- 2083
- 2087
- 2096
- 8443

After your port is set to one that is compatible, create a new A record for a subdomain that will be used for websocket connections, and make sure that it is set to be proxied by Cloudflare (the cloud should be orange if it is being proxied). It should be pointing to your emulator IP.

Finally, create a new page rule under the Page Rules tab in Cloudflare and disable SSL for the subdomain you created above. You will now be able to connect using secure websockets using the following example url, where I created an A record for the subdomain `ws` and I set my `ws.nitro.port` to 2096: `wss://ws.example.com:2096` 

### Branches ###
There are two main branches in use on the Arcturus Morningstar git. Below the pros an

| master | Tested on a production hotel and is stable for every day use with no known serious exploits. |
| ------------- | ------------- |
| dev | The most up-to-date, but features may not work as intended. Use at your own risk. |

There is no set timeframe on when new versions will be released or when the stable branch will be updated

### Database ###
We have placed the myBoBBa database [myBobba](https://github.com/ObjectRetros/2023-hotel-files) to get you started on building your Retro hotel.
Also there is a file call UpdateDatabase.sql this will hold all the updates that are required, please run this after the myBoBBa Database import

## Can I Help!? ##
#### Reporting Bugs: ####
You can report problems via the [Issue Tracker](https://github.com/duckietm/Arcturus-Morningstar-Extended/issues*

###### * When making an bug report or a feature request use the template we provide so that it can be categorized correctly and we have more information to replicate a bug or implement a feature correctly. ######
#### Can I contribute code to this project? ####
Of Course! if you have fixed a bug from the git please feel free to do a [merge request](https://github.com/duckietm/Arcturus-Morningstar-Extended/issues)*

## Support 
We also have a [Discord channel](https://discord.gg/3VeyZXf5) where you can find some more information.

###### * Anyone is allowed to fork the project and make pull requests, we make no guarantee that pull requests will be approved into the project. Please Do NOT push code which does not replicate behaviour on habbo.com, instead make the behaviour configurable or as a plugin. ######

## Plugin System ##
The robust Plugin System included in the original Arcturus release is also included in Arcturus Morningstar, if you're interested in making your own plugins, feel free to ask around on our discord and we'll point you in the right direction! 

### Credits ###
    
       - TheGeneral (Arcturus Emulator)
	   - Beny
	   - Alejandro
	   - Capheus
	   - Skeletor
	   - Harmonic
	   - Mike
	   - Remco
	   - zGrav
	   - Quadral
	   - Harmony
	   - Swirny
	   - ArpyAge
	   - Mikkel
	   - Rodolfo
	   - Kitt Mustang
	   - Snaiker
	   - nttzx
	   - necmi
	   - Dome
	   - Jose Flores
	   - Cam
	   - Oliver
	   - Narzo
	   - Tenshie
	   - MartenM
	   - Ridge
	   - SenpaiDipper
	   - Thijmen
	   - Brenoepic
	   - Stankman
	   - Laynester
	   - Bill
	   - Mikee
	   - Merijn
	   - Puffin
	   - ObjectRetros
	   - EntenKoeniq
	   - DuckieTM
