-- Please sort tables alphabetically.
-- And columns in what seems like a "good to know first" order,
-- maybe primary key first?


--======================================================================
--  link_previews_t
--======================================================================

------------------------------------------------------------------------
comment on table  link_previews_t  is $_$

[defense] Both link_url_c and downloaded_from_url_c are part of the primary key
— otherwise maybe an attacker could do something weird, like the following:

    An attacker's website atkws could hijack a widget from a normal
    website realws, like so:
      https://atkws/widget pretends in a html tag that its oEmbed endpoint is
      E = https://realws/oembed?url=https://realws/widget
    and then later when someone tries to link to https://realws/widget,
    whose oEmbed endpoint is E for real,
    then, if lookng up by E only,
    there'd already be oEmbed link_url_c for E, namely: https//atkws/widget
    (because E initially got saved via the request to https://atkws/widget)
    that is,
    for E = downloaded_from_url_c = https://realws/oembed?url=https://realws/widget,
    the link_url_c would point to the attacker's site.
    Not sure how this could be misused, but feels risky (even though
    the actual oEmbed contents would be from realws).
    
    But by including both link_url_c and downloaded_from_url_c in the primary key,
    that cannot happen — when looking up https://realws/widget + E,
    the attacker's entry wouldn't be found (because it's  https://atkws/...).

There's an index  linkpreviews_i_downl_err_at  you can use to maybe retry
failed downlads after a while.
$_$;


------------------------------------------------------------------------
comment on column  link_previews_t.link_url_c  is $_$

An extenal link that we want to show a preview for. E.g. a link to a Wikipedia page
or Twitter tweet or YouTube video, or an external image or blog post, whatever.
$_$;


------------------------------------------------------------------------
comment on column  link_previews_t.content_json_c  is $_$

Why up to 27 000 long? Well, this can be lots of data — an Instagram
oEmbed was 9 215 bytes, and included an inline <svg> image, and
'background-color: #F4F4F4' repeated at 8 places, and the Instagram post text
repeated twice. Better allow at least 2x more than that.
There's an appserver max length check too [oEmb_json_len].
$_$;


------------------------------------------------------------------------
comment on column  link_previews_t.status_code_c  is $_$

Is 0 if the request failed completely [ln_pv_netw_err], didn't get any response.
E.g. TCP RST or timeout. 0 means the same in a browser typically, e.g. request.abort().

However, currently (maybe always?) failed downloads are instead cached temporarily
only, in Redis, so cannot DoS attack the disk storage.  [ln_pv_downl_errs]
$_$;


------------------------------------------------------------------------
comment on column  link_previews_t.content_json_c  is $_$

Null if the request failed, got no response json. E.g. an error status code,
or a request timeout or TCP RST?   [ln_pv_downl_errs]
$_$;


--======================================================================
--
--======================================================================



