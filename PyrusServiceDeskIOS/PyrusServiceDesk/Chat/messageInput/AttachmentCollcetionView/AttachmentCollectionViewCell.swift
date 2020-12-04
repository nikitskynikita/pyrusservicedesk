//
//  AttachmentCollectionViewCell.swift
//  PyrusServiceDesk
//
//  Created by Галина  Муравьева on 20.04.2020.
//  Copyright © 2020  Галина Муравьева. All rights reserved.
//

import Foundation
protocol AttachmentCollectionViewCellDelegate : NSObjectProtocol{
    func removePressed(for cell:AttachmentCollectionViewCell)
}
class AttachmentCollectionViewCell : UICollectionViewCell{
    weak var delegate : AttachmentCollectionViewCellDelegate?
    private static let buttonSize : CGFloat = 15
    private var buttonImageName: String = {
        if PSD_TextColorForInput() == .white {
            return "ic_remove"
        }
        return "ic_remove_darkmode"
    }()
    static let distToBoard : CGFloat = 7
    private static let holderRadius : CGFloat = 6.0
    private static let holderBorderWidth : CGFloat = 1.0
    private lazy var removeButton : UIButton = {
        let button = UIButton()
        button.setImage(UIImage.PSDImage(name: buttonImageName), for: .normal)
        button.layer.cornerRadius = AttachmentCollectionViewCell.buttonSize/2
        return button
    }()
    private let removeButtonBack = UIView()
    lazy var holderView : UIView = {
        let view = UIView()
        view.backgroundColor = .clear
        view.layer.cornerRadius = AttachmentCollectionViewCell.holderRadius
        view.layer.borderWidth = AttachmentCollectionViewCell.holderBorderWidth
        view.clipsToBounds = true
        return view
    }()
    override init(frame: CGRect) {
        super.init(frame: frame)
        customInit()
    }
    
    required init?(coder: NSCoder) {
        super.init(coder: coder)
        customInit()
    }
    private func customInit(){
        addSubview(holderView)
        addSubview(removeButtonBack)
        removeButtonBack.addSubview(removeButton)
        removeButtonBack.translatesAutoresizingMaskIntoConstraints = false
        removeButton.translatesAutoresizingMaskIntoConstraints = false
        holderView.translatesAutoresizingMaskIntoConstraints = false
        
        holderView.leftAnchor.constraint(equalTo: leftAnchor, constant: AttachmentCollectionViewCell.distToBoard).isActive = true
        holderView.rightAnchor.constraint(equalTo: rightAnchor, constant: -AttachmentCollectionViewCell.distToBoard).isActive = true
        holderView.bottomAnchor.constraint(equalTo: bottomAnchor, constant: -AttachmentCollectionViewCell.distToBoard).isActive = true
        holderView.topAnchor.constraint(equalTo: removeButtonBack.topAnchor, constant: AttachmentCollectionViewCell.distToBoard).isActive = true
        
        removeButtonBack.rightAnchor.constraint(equalTo: rightAnchor, constant: 0).isActive = true
        removeButtonBack.topAnchor.constraint(equalTo: topAnchor, constant: DEFAULT_LAYOUT_MARGINS).isActive = true
        removeButtonBack.widthAnchor.constraint(equalToConstant: AttachmentCollectionViewCell.buttonSize).isActive = true
        removeButtonBack.heightAnchor.constraint(equalToConstant: AttachmentCollectionViewCell.buttonSize).isActive = true
        removeButton.addZeroConstraint([.top, .bottom, .left, .right])
        removeButton.addTarget(self, action: #selector(removeButtonPressed), for: .touchUpInside)
        removeButtonBack.layer.cornerRadius = AttachmentCollectionViewCell.buttonSize/2
        recolor()
    }
    @objc private func removeButtonPressed(){
        delegate?.removePressed(for: self)
    }
    override func traitCollectionDidChange(_ previousTraitCollection: UITraitCollection?) {
        super.traitCollectionDidChange(previousTraitCollection)
        if #available(iOS 13.0, *) {
            guard traitCollection.hasDifferentColorAppearance(comparedTo: previousTraitCollection) else {
                return
            }
            recolor()
        }
    }
    private func recolor() {
        removeButton.setImage(UIImage.PSDImage(name: buttonImageName), for: .normal)
        removeButton.alpha = BUTTON_ALPHA
        removeButtonBack.backgroundColor = PyrusServiceDesk.mainController?.customization?.keyboardColor ?? PSD_lightGrayInputColor
        holderView.layer.borderColor = PSD_lightGrayInputColor.cgColor
    }
}
let BUTTON_ALPHA: CGFloat = 0.6
