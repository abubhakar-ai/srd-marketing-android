from PIL import Image, ImageDraw, ImageFont
F='/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf'

def art(d, W, ox, oy, S, fg=(255,255,255,255), acc=(255,220,150,255)):
    f=ImageFont.truetype(F,int(S*0.36))
    t='SRD'
    b=d.textbbox((0,0),t,font=f)
    tw=b[2]-b[0]
    d.text((ox+(S-tw)/2-b[0], oy+S*0.02-b[1]), t, font=f, fill=fg)
    base=oy+S*0.98
    bw=S*0.15; gap=S*0.08
    total=3*bw+2*gap; x0=ox+(S-total)/2
    hs=[0.14,0.24,0.36]
    tops=[]
    for i,h in enumerate(hs):
        x=x0+i*(bw+gap)
        d.rounded_rectangle([x,base-S*h,x+bw,base],radius=bw*0.28,fill=acc)
        tops.append((x+bw/2,base-S*h))
    pts=[(tops[0][0]-bw*0.2,tops[0][1]-S*0.07),(tops[1][0],tops[1][1]-S*0.09),(tops[2][0]+bw*0.1,tops[2][1]-S*0.12)]
    d.line(pts,fill=fg,width=max(2,int(S*0.045)),joint='curve')
    hx,hy=pts[-1]
    d.polygon([(hx+S*0.10,hy-S*0.04),(hx-S*0.03,hy-S*0.07),(hx+S*0.05,hy+S*0.07)],fill=fg)
def grad(W,c1,c2):
    im=Image.new('RGBA',(W,W))
    px=im.load()
    for y in range(W):
        for x in range(W):
            t=(x+y)/(2*W)
            px[x,y]=tuple(int(c1[i]+(c2[i]-c1[i])*t) for i in range(3))+(255,)
    return im
W=192
im=grad(W,(255,138,61),(226,84,10))
m=Image.new('L',(W,W),0); ImageDraw.Draw(m).rounded_rectangle([0,0,W-1,W-1],radius=44,fill=255)
out=Image.new('RGBA',(W,W),(0,0,0,0)); out.paste(im,(0,0),m)
art(ImageDraw.Draw(out),W,W*0.17,W*0.12,W*0.66)
out.save('ic_srd.png',optimize=True)
W=324
fg=Image.new('RGBA',(W,W),(0,0,0,0))
art(ImageDraw.Draw(fg),W,W*0.25,W*0.22,W*0.5)
fg.save('ic_srd_fg.png',optimize=True)
