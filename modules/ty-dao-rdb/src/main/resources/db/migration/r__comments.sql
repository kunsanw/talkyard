-- Please sort tables alphabetically.
-- And columns in what seems like a "good to know first" order,
-- maybe primary key first?


------------------------------------------------------------------------
--  link_previews_t
------------------------------------------------------------------------


comment on table  link_previews_t  is $_$

Both link_url_c and downloaded_from_url_c are part of the primary key
— otherwise an attacker's website A could hijack a widget from a normal
website W, like so:
  https://webs-a/widget pretends in a html tag that its oEmbed endpoint is
  E = https://webs-w/oembed?url=https://webs-w/widget  (note: webs-w, not -a)
and then later when someone tries to link to https://webs-w/widget,
whose oEmbed endpoint is E for real,
then, if lookng up by E only,
there'd already be a link_url_c = https//webs-a/widget associated with E,
i.e. pointing to the attacker's site.

But by including both link_url_c and downloaded_from_url_c in the primary key,
that cannot happen — when looking up https://webs-w/widget + E,
the attacker's entry wouldn't be found (because it's  https://webs-a/...).

There's an index  linkpreviews_i_site_downl_err_at  you can use to maybe retry
failed downlads after a while.
$_$;


comment on column  link_previews_t.link_url_c  is $_$

A link to a Wikipedia page or Twitter tweet or YouTube video,
or an external image or blog post, whatever. That linked thing
is what we want to show a preview of, in Talkyard.
$_$;


comment on column  link_previews_t.content_json_c  is $_$

Why up to 27 000 long? [oEmb_json_len] Well, this can be lots of data — an Instagram
oEmbed was 9 215 bytes, and contains an inline <svg> image, and
'background-color: #F4F4F4' repeated at 8 places, and the Instagram post text
repeated twice. Better allow at least 2x more than that.
$_$;


comment on column  link_previews_t.status_code_c  is $_$

Is 0 if the request failed completely [ln_pv_netw_err], didn't get any response. E.g.
TCP RST or timeout. 0 means the same in a browser typically, e.g. request.abort().
$_$;


comment on column  link_previews_t.content_json_c  is $_$

Null if the request failed, got no response json. E.g. an error status code,
or a request timeout or TCP RST?
$_$;


------------------------------------------------------------------------
--
------------------------------------------------------------------------



